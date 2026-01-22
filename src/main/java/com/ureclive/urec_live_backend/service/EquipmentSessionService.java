package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.EquipmentStatusUpdate;
import com.ureclive.urec_live_backend.entity.*;
import com.ureclive.urec_live_backend.repository.EquipmentEventRepository;
import com.ureclive.urec_live_backend.repository.EquipmentSessionRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

@Service
public class EquipmentSessionService {

    private static final Logger logger = LoggerFactory.getLogger(EquipmentSessionService.class);

    private final EquipmentSessionRepository sessionRepository;
    private final EquipmentEventRepository eventRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public EquipmentSessionService(
            EquipmentSessionRepository sessionRepository,
            EquipmentEventRepository eventRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public EquipmentSession startSession(User user, Equipment equipment, String metadata) {
        // Prevent double-booking the same equipment
        sessionRepository.findByEquipmentAndStatus(equipment, EquipmentSessionStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new IllegalStateException("Equipment is already in use. Active session id=" + existing.getId());
                });

        Instant now = Instant.now();

        // Create session
        EquipmentSession session = new EquipmentSession();
        session.setUser(user);
        session.setEquipment(equipment);
        session.setStatus(EquipmentSessionStatus.ACTIVE);
        session.setStartedAt(now);

        session = sessionRepository.save(session);

        // Audit event
        EquipmentEvent ev = new EquipmentEvent();
        ev.setEventType(EquipmentEventType.SESSION_STARTED);
        ev.setEquipment(equipment);
        ev.setSession(session);
        ev.setUser(user);
        ev.setOccurredAt(now);
        ev.setMetadata(metadata);
        eventRepository.save(ev);

        // Broadcast live status update
        publishStatus(equipment.getId(), "IN_USE", session.getId(), user.getId(), now);

        return session;
    }

    @Transactional
    public EquipmentSession endSession(User user, Equipment equipment, String metadata) {
        EquipmentSession session = sessionRepository.findByEquipmentAndStatus(equipment, EquipmentSessionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active session found for this equipment"));

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

        return session;
    }

    @Transactional(readOnly = true)
    public Optional<EquipmentSession> getMyActiveSession(User user) {
        return sessionRepository.findTopByUserAndStatusOrderByStartedAtDesc(user, EquipmentSessionStatus.ACTIVE);
    }

    private void publishStatus(Long equipmentId, String status, Long sessionId, Long userId, Instant occurredAt) {
        EquipmentStatusUpdate payload = new EquipmentStatusUpdate(
                equipmentId,
                status,
                sessionId,
                userId,
                occurredAt
        );
        logger.info("[WS] publishing equipment-status: {} -> {}", equipmentId, status);
        messagingTemplate.convertAndSend("/topic/equipment/" + equipmentId, payload);
        messagingTemplate.convertAndSend("/topic/equipment-status", payload);
    }
}
