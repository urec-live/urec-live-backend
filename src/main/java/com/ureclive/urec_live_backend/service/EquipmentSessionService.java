package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.*;
import com.ureclive.urec_live_backend.repository.EquipmentEventRepository;
import com.ureclive.urec_live_backend.repository.EquipmentSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EquipmentSessionService {

    private final EquipmentSessionRepository sessionRepository;
    private final EquipmentEventRepository eventRepository;

    public EquipmentSessionService(
            EquipmentSessionRepository sessionRepository,
            EquipmentEventRepository eventRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EquipmentSession startSession(User user, Equipment equipment, String metadata) {
        // Optional safety: ensure no ACTIVE session exists for this equipment
        sessionRepository.findByEquipmentAndStatus(equipment, EquipmentSessionStatus.ACTIVE)
                .ifPresent(s -> {
                    throw new IllegalStateException("Equipment already has an active session: " + equipment.getId());
                });

        EquipmentSession session = new EquipmentSession();
        session.setUser(user);
        session.setEquipment(equipment);
        session.setStatus(EquipmentSessionStatus.ACTIVE);
        session.setStartedAt(Instant.now());

        EquipmentSession saved = sessionRepository.save(session);

        EquipmentEvent event = new EquipmentEvent();
        event.setEventType(EquipmentEventType.SESSION_STARTED);
        event.setEquipment(equipment);
        event.setUser(user);
        event.setSession(saved);
        event.setOccurredAt(Instant.now());
        event.setMetadata(metadata);

        eventRepository.save(event);

        return saved;
    }

    @Transactional
    public EquipmentSession endSession(User user, Equipment equipment, String metadata) {
        EquipmentSession session = sessionRepository
                .findByUserAndEquipmentAndStatus(user, equipment, EquipmentSessionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active session found for this user + equipment."));

        session.setStatus(EquipmentSessionStatus.ENDED);
        session.setEndedAt(Instant.now());
        session.setEndReason(EquipmentSessionEndReason.USER_ENDED);

        EquipmentSession saved = sessionRepository.save(session);

        EquipmentEvent event = new EquipmentEvent();
        event.setEventType(EquipmentEventType.SESSION_ENDED);
        event.setEquipment(equipment);
        event.setUser(user);
        event.setSession(saved);
        event.setOccurredAt(Instant.now());
        event.setMetadata(metadata);

        eventRepository.save(event);

        return saved;
    }

    @Transactional
    public EquipmentSession timeoutSession(EquipmentSession session, String metadata) {
        if (session.getStatus() != EquipmentSessionStatus.ACTIVE) {
            return session; // ignore if not active
        }

        session.setStatus(EquipmentSessionStatus.TIMED_OUT);
        session.setEndedAt(Instant.now());
        session.setEndReason(EquipmentSessionEndReason.TIMEOUT);

        EquipmentSession saved = sessionRepository.save(session);

        EquipmentEvent event = new EquipmentEvent();
        event.setEventType(EquipmentEventType.SESSION_TIMED_OUT);
        event.setEquipment(saved.getEquipment());
        event.setUser(saved.getUser());
        event.setSession(saved);
        event.setOccurredAt(Instant.now());
        event.setMetadata(metadata);

        eventRepository.save(event);

        return saved;
    }
}
