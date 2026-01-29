package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.EquipmentStatusUpdate;
import com.ureclive.urec_live_backend.event.EquipmentStatusPublishEvent;
import com.ureclive.urec_live_backend.entity.*;
import com.ureclive.urec_live_backend.repository.EquipmentEventRepository;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.EquipmentSessionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class EquipmentSessionService {

    private static final Logger logger = LoggerFactory.getLogger(EquipmentSessionService.class);

    private final EquipmentSessionRepository sessionRepository;
    private final EquipmentEventRepository eventRepository;
    private final EquipmentRepository equipmentRepository;
    private final long sessionTimeoutMinutes;
    private final long sessionTimeoutWarningMinutes;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final PushNotificationService pushNotificationService;

    public EquipmentSessionService(
            EquipmentSessionRepository sessionRepository,
            EquipmentEventRepository eventRepository,
            EquipmentRepository equipmentRepository,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry,
            PushNotificationService pushNotificationService,
            @Value("${session.timeout.minutes:15}") long sessionTimeoutMinutes,
            @Value("${session.timeout.warning-minutes:2}") long sessionTimeoutWarningMinutes) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.equipmentRepository = equipmentRepository;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.pushNotificationService = pushNotificationService;
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
        this.sessionTimeoutWarningMinutes = sessionTimeoutWarningMinutes;
        meterRegistry.gauge("urec.sessions.active", sessionRepository,
                repo -> repo.countByStatus(EquipmentSessionStatus.ACTIVE));
    }

    @Transactional
    public EquipmentSession startSession(User user, Equipment equipment, String metadata) {
        logger.info("[session] start requested user={} equipment={} metadata={}", user.getId(), equipment.getId(),
                metadata);
        Equipment lockedEquipment = equipmentRepository.findByIdForUpdate(equipment.getId())
                .orElseThrow(() -> new IllegalStateException("Equipment not found id=" + equipment.getId()));

        // Prevent a user from starting multiple sessions
        Optional<EquipmentSession> activeForUser = sessionRepository.findTopByUserAndStatusOrderByStartedAtDesc(user,
                EquipmentSessionStatus.ACTIVE);
        if (activeForUser.isPresent()) {
            EquipmentSession existing = activeForUser.get();
            if (existing.getEquipment() != null && existing.getEquipment().getId().equals(lockedEquipment.getId())) {
                return existing;
            }
            logger.warn(
                    "event=conflict_rejected reason=user_active_session userId={} equipmentId={} activeSessionId={}",
                    user.getId(), lockedEquipment.getId(), existing.getId());
            meterRegistry.counter("urec.sessions.conflict", "reason", "user_active_session").increment();
            throw new IllegalStateException("User already has an active session id=" + existing.getId());
        }

        // Prevent double-booking the same equipment
        Optional<EquipmentSession> activeForEquipment = sessionRepository.findByEquipmentAndStatus(lockedEquipment,
                EquipmentSessionStatus.ACTIVE);
        if (activeForEquipment.isPresent()) {
            EquipmentSession existing = activeForEquipment.get();
            if (existing.getUser() != null && existing.getUser().getId().equals(user.getId())) {
                return existing;
            }
            logger.warn("event=conflict_rejected reason=equipment_in_use userId={} equipmentId={} activeSessionId={}",
                    user.getId(), lockedEquipment.getId(), existing.getId());
            meterRegistry.counter("urec.sessions.conflict", "reason", "equipment_in_use").increment();
            throw new IllegalStateException("Equipment is already in use. Active session id=" + existing.getId());
        }

        Instant now = Instant.now();

        // Create session
        EquipmentSession session = new EquipmentSession();
        session.setUser(user);
        session.setEquipment(lockedEquipment);
        session.setStatus(EquipmentSessionStatus.ACTIVE);
        session.setStartedAt(now);
        session.setLastHeartbeatAt(now);

        session = sessionRepository.save(session);

        // Audit event
        EquipmentEvent ev = new EquipmentEvent();
        ev.setEventType(EquipmentEventType.SESSION_STARTED);
        ev.setEquipment(lockedEquipment);
        ev.setSession(session);
        ev.setUser(user);
        ev.setOccurredAt(now);
        ev.setMetadata(metadata);
        eventRepository.save(ev);

        // Broadcast live status update
        publishStatus(lockedEquipment.getId(), "IN_USE", session.getId(), user.getId(), now);
        meterRegistry.counter("urec.sessions.lifecycle", "event", "start").increment();
        logger.info("[session] started session={} user={} equipment={}", session.getId(), user.getId(),
                lockedEquipment.getId());
        logger.info("event=session_start sessionId={} userId={} equipmentId={} occurredAt={} metadata={}",
                session.getId(), user.getId(), lockedEquipment.getId(), now, metadata);

        return session;
    }

    @Transactional
    public EquipmentSession endSession(User user, Equipment equipment, String metadata) {
        logger.info("[session] end requested user={} equipment={} metadata={}", user.getId(), equipment.getId(),
                metadata);
        EquipmentSession session = sessionRepository.findByEquipmentAndStatus(equipment, EquipmentSessionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active session found for this equipment"));

        if (session.getStartedAt() == null) {
            throw new IllegalStateException("Active session is missing startedAt.");
        }

        // Optional: enforce only the same user can end their session
        if (!session.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Only the session owner can end this session.");
        }

        Instant now = Instant.now();

        session.setStatus(EquipmentSessionStatus.ENDED);
        session.setEndedAt(now);
        session.setEndReason(EquipmentSessionEndReason.USER_ENDED);
        session = sessionRepository.save(session);

        // Audit event
        EquipmentEvent ev = new EquipmentEvent();
        ev.setEventType(EquipmentEventType.SESSION_ENDED);
        ev.setEquipment(equipment);
        ev.setSession(session);
        ev.setUser(user);
        ev.setOccurredAt(now);
        ev.setMetadata(metadata);
        eventRepository.save(ev);

        // Broadcast live status update
        publishStatus(equipment.getId(), "AVAILABLE", null, null, now);
        meterRegistry.counter("urec.sessions.lifecycle", "event", "end").increment();
        logger.info("[session] ended session={} user={} equipment={} reason={}", session.getId(), user.getId(),
                equipment.getId(), session.getEndReason());
        logger.info("event=session_end sessionId={} userId={} equipmentId={} occurredAt={} reason={} metadata={}",
                session.getId(), user.getId(), equipment.getId(), now, session.getEndReason(), metadata);

        return session;
    }

    @Transactional(readOnly = true)
    public Optional<EquipmentSession> getMyActiveSession(User user) {
        return sessionRepository.findTopByUserAndStatusOrderByStartedAtDesc(user, EquipmentSessionStatus.ACTIVE);
    }

    @Transactional
    public EquipmentSession heartbeat(User user, Equipment equipment) {
        EquipmentSession session = sessionRepository.findByEquipmentAndStatus(equipment, EquipmentSessionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active session found for this equipment"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Only the session owner can heartbeat this session.");
        }

        Instant now = Instant.now();
        session.setLastHeartbeatAt(now);
        EquipmentSession updated = sessionRepository.save(session);
        meterRegistry.counter("urec.sessions.lifecycle", "event", "heartbeat").increment();
        logger.info("event=session_heartbeat sessionId={} userId={} equipmentId={} occurredAt={}",
                updated.getId(), user.getId(), equipment.getId(), now);
        return updated;
    }

    @Scheduled(fixedDelayString = "${session.timeout.check-ms:60000}")
    @Transactional
    public void timeoutStaleSessions() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(sessionTimeoutMinutes));
        if (sessionTimeoutWarningMinutes > 0 && sessionTimeoutWarningMinutes < sessionTimeoutMinutes) {
            Instant warnCutoff = Instant.now()
                    .minus(Duration.ofMinutes(sessionTimeoutMinutes - sessionTimeoutWarningMinutes));
            List<EquipmentSession> warnSessions = sessionRepository
                    .findSessionsNeedingTimeoutWarning(EquipmentSessionStatus.ACTIVE, warnCutoff);
            for (EquipmentSession session : warnSessions) {
                sendTimeoutWarning(session, Instant.now());
            }
        }

        List<EquipmentSession> staleSessions = sessionRepository.findStaleSessions(EquipmentSessionStatus.ACTIVE,
                cutoff);

        if (staleSessions.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        for (EquipmentSession session : staleSessions) {
            if (session.getStartedAt() == null) {
                logger.warn("[WS] skipping timeout for session {} (missing startedAt)", session.getId());
                continue;
            }

            session.setStatus(EquipmentSessionStatus.TIMED_OUT);
            session.setEndedAt(now);
            session.setEndReason(EquipmentSessionEndReason.TIMEOUT);
            sessionRepository.save(session);

            Equipment equipment = session.getEquipment();
            User user = session.getUser();

            EquipmentEvent ev = new EquipmentEvent();
            ev.setEventType(EquipmentEventType.SESSION_TIMED_OUT);
            ev.setEquipment(equipment);
            ev.setSession(session);
            ev.setUser(user);
            ev.setOccurredAt(now);
            ev.setMetadata("auto-timeout");
            eventRepository.save(ev);

            publishStatus(equipment.getId(), "AVAILABLE", null, null, now);
            meterRegistry.counter("urec.sessions.lifecycle", "event", "timeout").increment();
            logger.info("[session] timed out session={} user={} equipment={}", session.getId(), user.getId(),
                    equipment.getId());
            logger.info("event=session_timeout sessionId={} userId={} equipmentId={} occurredAt={} reason=TIMEOUT",
                    session.getId(), user.getId(), equipment.getId(), now);

            pushNotificationService.sendToUser(
                    user,
                    "Session ended",
                    "Your session on " + equipment.getName() + " ended due to inactivity.",
                    java.util.Map.of(
                            "type", "SESSION_TIMED_OUT",
                            "sessionId", session.getId(),
                            "equipmentId", equipment.getId()));
        }
    }

    private void sendTimeoutWarning(EquipmentSession session, Instant now) {
        if (session.getEquipment() == null || session.getUser() == null) {
            return;
        }
        session.setLastTimeoutWarningAt(now);
        sessionRepository.save(session);

        Equipment equipment = session.getEquipment();
        User user = session.getUser();

        publishStatus(equipment.getId(), "TIMEOUT_WARNING", session.getId(), user.getId(), now);
        meterRegistry.counter("urec.sessions.lifecycle", "event", "timeout_warning").increment();
        logger.info(
                "event=session_timeout_warning sessionId={} userId={} equipmentId={} occurredAt={} minutesRemaining={}",
                session.getId(), user.getId(), equipment.getId(), now, sessionTimeoutWarningMinutes);

        pushNotificationService.sendToUser(
                user,
                "Session ending soon",
                "Your session on " + equipment.getName() + " will end soon. Tap to keep it active.",
                java.util.Map.of(
                        "type", "TIMEOUT_WARNING",
                        "sessionId", session.getId(),
                        "equipmentId", equipment.getId()));
    }

    private void publishStatus(Long equipmentId, String status, Long sessionId, Long userId, Instant occurredAt) {
        EquipmentStatusUpdate payload = new EquipmentStatusUpdate(
                equipmentId,
                status,
                sessionId,
                userId,
                occurredAt);
        logger.info("event=ws_publish_queued equipmentId={} status={}", equipmentId, status);
        eventPublisher.publishEvent(new EquipmentStatusPublishEvent(payload));
    }
}
