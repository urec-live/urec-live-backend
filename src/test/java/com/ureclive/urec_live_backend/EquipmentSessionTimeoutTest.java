package com.ureclive.urec_live_backend;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentEvent;
import com.ureclive.urec_live_backend.entity.EquipmentEventType;
import com.ureclive.urec_live_backend.entity.EquipmentSession;
import com.ureclive.urec_live_backend.entity.EquipmentSessionEndReason;
import com.ureclive.urec_live_backend.entity.EquipmentSessionStatus;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentEventRepository;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.EquipmentSessionRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.service.EquipmentSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class EquipmentSessionTimeoutTest {

    @Autowired
    private EquipmentSessionService equipmentSessionService;

    @Autowired
    private EquipmentSessionRepository equipmentSessionRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EquipmentEventRepository equipmentEventRepository;

    @Test
    @Transactional
    void timesOutStaleSessionAndCreatesEvent() {
        final User user = userRepository.save(
                new User("timeout_user", "timeout_user@example.com", "password")
        );

        final Equipment equipment = equipmentRepository.save(
                new Equipment("TIMEOUT01", "Timeout Machine", "AVAILABLE", null)
        );

        EquipmentSession session = new EquipmentSession();
        session.setUser(user);
        session.setEquipment(equipment);
        session.setStatus(EquipmentSessionStatus.ACTIVE);
        session.setStartedAt(Instant.now().minus(Duration.ofHours(4)));
        session = equipmentSessionRepository.save(session);
        final long sessionId = session.getId();

        equipmentSessionService.timeoutStaleSessions();

        EquipmentSession updated = equipmentSessionRepository.findById(sessionId).orElseThrow();
        assertEquals(EquipmentSessionStatus.TIMED_OUT, updated.getStatus());
        assertEquals(EquipmentSessionEndReason.TIMEOUT, updated.getEndReason());
        assertNotNull(updated.getEndedAt());

        List<EquipmentEvent> events = equipmentEventRepository.findAll();
        List<EquipmentEvent> sessionEvents = events.stream()
                .filter(event -> event.getSession() != null && event.getSession().getId() == sessionId)
                .collect(Collectors.toList());
        long timeoutEvents = sessionEvents.stream()
                .filter(event -> event.getEventType() == EquipmentEventType.SESSION_TIMED_OUT)
                .count();
        assertEquals(1, timeoutEvents);
        assertEquals(1, sessionEvents.size());
    }

    @Test
    @Transactional
    void heartbeatPreventsTimeout() {
        final String suffix = String.valueOf(System.currentTimeMillis());
        final User user = userRepository.save(
                new User("heartbeat_user_" + suffix, "heartbeat_" + suffix + "@example.com", "password")
        );

        final Equipment equipment = equipmentRepository.save(
                new Equipment("HEART01_" + suffix, "Heartbeat Machine", "AVAILABLE", null)
        );

        EquipmentSession session = new EquipmentSession();
        session.setUser(user);
        session.setEquipment(equipment);
        session.setStatus(EquipmentSessionStatus.ACTIVE);
        session.setStartedAt(Instant.now().minus(Duration.ofHours(4)));
        session.setLastHeartbeatAt(Instant.now());
        session = equipmentSessionRepository.save(session);
        final long sessionId = session.getId();

        equipmentSessionService.timeoutStaleSessions();

        EquipmentSession updated = equipmentSessionRepository.findById(sessionId).orElseThrow();
        assertEquals(EquipmentSessionStatus.ACTIVE, updated.getStatus());
        assertTrue(updated.getEndedAt() == null);
    }

    @Test
    @Transactional
    void sendsTimeoutWarningBeforeEnding() {
        final String suffix = String.valueOf(System.currentTimeMillis());
        final User user = userRepository.save(
                new User("warn_user_" + suffix, "warn_" + suffix + "@example.com", "password")
        );

        final Equipment equipment = equipmentRepository.save(
                new Equipment("WARN01_" + suffix, "Warning Machine", "AVAILABLE", null)
        );

        EquipmentSession session = new EquipmentSession();
        session.setUser(user);
        session.setEquipment(equipment);
        session.setStatus(EquipmentSessionStatus.ACTIVE);
        Long timeoutMinutesObj = (Long) ReflectionTestUtils.getField(
                Objects.requireNonNull(equipmentSessionService),
                "sessionTimeoutMinutes"
        );
        Long warningMinutesObj = (Long) ReflectionTestUtils.getField(
                Objects.requireNonNull(equipmentSessionService),
                "sessionTimeoutWarningMinutes"
        );
        long timeoutMinutes = timeoutMinutesObj == null ? 15L : timeoutMinutesObj;
        long warningMinutes = warningMinutesObj == null ? 2L : warningMinutesObj;
        long minutesBeforeTimeout = Math.max(1, timeoutMinutes - warningMinutes + 1);
        Instant now = Instant.now();
        session.setStartedAt(now.minus(Duration.ofMinutes(timeoutMinutes + 5)));
        session.setLastHeartbeatAt(now.minus(Duration.ofMinutes(minutesBeforeTimeout)));
        Long sessionId = Objects.requireNonNull(
                equipmentSessionRepository.save(session).getId(),
                "Expected session id"
        );
        equipmentSessionRepository.flush();

        equipmentSessionService.timeoutStaleSessions();

        EquipmentSession updated = equipmentSessionRepository.findById(sessionId).orElseThrow();
        assertEquals(EquipmentSessionStatus.ACTIVE, updated.getStatus());
        assertNotNull(updated.getLastTimeoutWarningAt());

    }

    @Test
    @Transactional
    void endSessionWithoutActiveSessionThrows() {
        final User user = userRepository.save(
                new User("no_session_user", "no_session_user@example.com", "password")
        );

        final Equipment equipment = equipmentRepository.save(
                new Equipment("NOSESSION01", "No Session Machine", "AVAILABLE", null)
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> equipmentSessionService.endSession(user, equipment, "cli_test")
        );
        assertEquals("No active session found for this equipment", ex.getMessage());
        assertTrue(equipmentSessionRepository.findByEquipmentAndStatus(
                equipment, EquipmentSessionStatus.ACTIVE
        ).isEmpty());
        long equipmentEvents = equipmentEventRepository.findAll().stream()
                .filter(event -> event.getEquipment() != null
                        && event.getEquipment().getId().equals(equipment.getId()))
                .count();
        assertEquals(0, equipmentEvents);
    }

    @Test
    @Transactional
    void endSessionByDifferentUserThrows() {
        final User owner = userRepository.save(
                new User("owner_user", "owner_user@example.com", "password")
        );
        final User otherUser = userRepository.save(
                new User("other_user", "other_user@example.com", "password")
        );

        final Equipment equipment = equipmentRepository.save(
                new Equipment("OWNER01", "Owner Machine", "AVAILABLE", null)
        );

        equipmentSessionService.startSession(owner, equipment, "cli_test");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> equipmentSessionService.endSession(otherUser, equipment, "cli_test")
        );
        assertEquals("Only the session owner can end this session.", ex.getMessage());

        EquipmentSession active = equipmentSessionRepository
                .findByEquipmentAndStatus(equipment, EquipmentSessionStatus.ACTIVE)
                .orElseThrow();
        assertEquals(owner.getId(), active.getUser().getId());
        assertTrue(active.getEndedAt() == null);
    }

    @Test
    @Transactional
    void startSessionPreventsDoubleBooking() {
        final User user = userRepository.save(
                new User("double_start_user", "double_start_user@example.com", "password")
        );

        final Equipment equipment = equipmentRepository.save(
                new Equipment("DOUBLE01", "Double Start Machine", "AVAILABLE", null)
        );

        EquipmentSession first = equipmentSessionService.startSession(user, equipment, "cli_test");
        EquipmentSession second = equipmentSessionService.startSession(user, equipment, "cli_test");
        assertEquals(first.getId(), second.getId());

        List<EquipmentSession> activeSessions = equipmentSessionRepository.findAll().stream()
                .filter(session -> session.getStatus() == EquipmentSessionStatus.ACTIVE)
                .filter(session -> session.getEquipment().getId().equals(equipment.getId()))
                .collect(Collectors.toList());
        assertEquals(1, activeSessions.size());

        long startEvents = equipmentEventRepository.findAll().stream()
                .filter(event -> event.getEquipment() != null
                        && event.getEquipment().getId().equals(equipment.getId())
                        && event.getEventType() == EquipmentEventType.SESSION_STARTED)
                .count();
        assertEquals(1, startEvents);
    }

    @Test
    @Transactional
    void startSessionPreventsMultipleActiveSessionsPerUser() {
        final String suffix = String.valueOf(System.currentTimeMillis());
        final User user = userRepository.save(
                new User("multi_session_user_" + suffix, "multi_session_user_" + suffix + "@example.com", "password")
        );

        final Equipment first = equipmentRepository.save(
                new Equipment("MULTI01_" + suffix, "Multi Session Machine 1", "AVAILABLE", null)
        );
        final Equipment second = equipmentRepository.save(
                new Equipment("MULTI02_" + suffix, "Multi Session Machine 2", "AVAILABLE", null)
        );

        equipmentSessionService.startSession(user, first, "cli_test");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> equipmentSessionService.startSession(user, second, "cli_test")
        );
        assertTrue(ex.getMessage().startsWith("User already has an active session"));

        long activeForUser = equipmentSessionRepository.findAll().stream()
                .filter(session -> session.getUser().getId().equals(user.getId()))
                .filter(session -> session.getStatus() == EquipmentSessionStatus.ACTIVE)
                .count();
        assertEquals(1, activeForUser);
    }

    @Test
    @Transactional
    void startSessionRejectedForSecondUserOnSameEquipment() {
        final String suffix = String.valueOf(System.currentTimeMillis());
        final User userOne = userRepository.save(
                new User("concurrent_user_a_" + suffix, "concurrent_a_" + suffix + "@example.com", "password")
        );
        final User userTwo = userRepository.save(
                new User("concurrent_user_b_" + suffix, "concurrent_b_" + suffix + "@example.com", "password")
        );
        final Equipment equipment = equipmentRepository.save(
                new Equipment("CONCUR01_" + suffix, "Concurrent Machine", "AVAILABLE", null)
        );

        equipmentSessionService.startSession(userOne, equipment, "cli_test");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> equipmentSessionService.startSession(userTwo, equipment, "cli_test")
        );
        assertTrue(ex.getMessage().startsWith("Equipment is already in use"));

        EquipmentSession active = equipmentSessionRepository
                .findByEquipmentAndStatus(equipment, EquipmentSessionStatus.ACTIVE)
                .orElseThrow();
        assertEquals(userOne.getId(), active.getUser().getId());
    }

    @Test
    @Transactional
    void sessionLifecycleCreatesStartAndEndEvents() {
        final String suffix = String.valueOf(System.currentTimeMillis());
        final User user = userRepository.save(
                new User("lifecycle_user_" + suffix, "lifecycle_" + suffix + "@example.com", "password")
        );
        final Equipment equipment = equipmentRepository.save(
                new Equipment("LIFE01_" + suffix, "Lifecycle Machine", "AVAILABLE", null)
        );

        EquipmentSession session = equipmentSessionService.startSession(user, equipment, "cli_test");
        equipmentSessionService.endSession(user, equipment, "cli_test");

        Long sessionId = session.getId();
        assertNotNull(sessionId);
        EquipmentSession ended = equipmentSessionRepository.findById(sessionId).orElseThrow();
        assertEquals(EquipmentSessionStatus.ENDED, ended.getStatus());
        assertEquals(EquipmentSessionEndReason.USER_ENDED, ended.getEndReason());
        assertNotNull(ended.getEndedAt());

        List<EquipmentEvent> sessionEvents = equipmentEventRepository.findAll().stream()
                .filter(event -> event.getSession() != null && event.getSession().getId().equals(session.getId()))
                .collect(Collectors.toList());
        long startCount = sessionEvents.stream()
                .filter(event -> event.getEventType() == EquipmentEventType.SESSION_STARTED)
                .count();
        long endCount = sessionEvents.stream()
                .filter(event -> event.getEventType() == EquipmentEventType.SESSION_ENDED)
                .count();
        assertEquals(1, startCount);
        assertEquals(1, endCount);
        assertEquals(2, sessionEvents.size());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentStartAllowsOnlyOneActiveSessionForEquipment() throws Exception {
        final String suffix = String.valueOf(System.currentTimeMillis());
        final User userOne = userRepository.save(
                new User("concurrent_user_1_" + suffix, "concurrent_1_" + suffix + "@example.com", "password")
        );
        final User userTwo = userRepository.save(
                new User("concurrent_user_2_" + suffix, "concurrent_2_" + suffix + "@example.com", "password")
        );
        final Equipment equipment = equipmentRepository.save(
                new Equipment("CONCUR_EQ_" + suffix, "Concurrent Equipment", "AVAILABLE", null)
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();

        futures.add(executor.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                EquipmentSession session = equipmentSessionService.startSession(userOne, equipment, "cli_test");
                return session.getId();
            } catch (Exception ex) {
                return ex;
            }
        }));

        futures.add(executor.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                EquipmentSession session = equipmentSessionService.startSession(userTwo, equipment, "cli_test");
                return session.getId();
            } catch (Exception ex) {
                return ex;
            }
        }));

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        int successCount = 0;
        for (Future<Object> future : futures) {
            Object result = future.get(5, TimeUnit.SECONDS);
            if (result instanceof Long) {
                successCount++;
            }
        }
        executor.shutdownNow();

        assertEquals(1, successCount);

        long activeCount = equipmentSessionRepository.findAll().stream()
                .filter(session -> session.getEquipment().getId().equals(equipment.getId()))
                .filter(session -> session.getStatus() == EquipmentSessionStatus.ACTIVE)
                .count();
        assertEquals(1, activeCount);

        long startEvents = equipmentEventRepository.findAll().stream()
                .filter(event -> event.getEquipment() != null
                        && event.getEquipment().getId().equals(equipment.getId())
                        && event.getEventType() == EquipmentEventType.SESSION_STARTED)
                .count();
        assertEquals(1, startEvents);
    }

}
