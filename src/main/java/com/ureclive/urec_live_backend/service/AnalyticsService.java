package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.EquipmentUtilizationPoint;
import com.ureclive.urec_live_backend.dto.EquipmentUtilizationSnapshot;
import com.ureclive.urec_live_backend.dto.EquipmentUtilizationSummary;
import com.ureclive.urec_live_backend.dto.EquipmentWaitTimeEstimate;
import com.ureclive.urec_live_backend.dto.EquipmentEventDto;
import com.ureclive.urec_live_backend.dto.SessionUsageSummary;
import com.ureclive.urec_live_backend.dto.SystemAnalyticsSummary;
import com.ureclive.urec_live_backend.dto.UserAnalyticsSummary;
import com.ureclive.urec_live_backend.dto.DataQualityAudit;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentEvent;
import com.ureclive.urec_live_backend.entity.EquipmentEventType;
import com.ureclive.urec_live_backend.entity.EquipmentSession;
import com.ureclive.urec_live_backend.entity.EquipmentSessionEndReason;
import com.ureclive.urec_live_backend.entity.EquipmentSessionStatus;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.EquipmentEventRepository;
import com.ureclive.urec_live_backend.repository.EquipmentSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AnalyticsService {
    private final EquipmentSessionRepository equipmentSessionRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentEventRepository equipmentEventRepository;
    private final int minHistoryForWaitTime;
    private final long sessionTimeoutMinutes;

    public AnalyticsService(
            EquipmentSessionRepository equipmentSessionRepository,
            EquipmentRepository equipmentRepository,
            EquipmentEventRepository equipmentEventRepository,
            @Value("${analytics.wait-time.min-history:5}") int minHistoryForWaitTime,
            @Value("${session.timeout.minutes:120}") long sessionTimeoutMinutes
    ) {
        this.equipmentSessionRepository = equipmentSessionRepository;
        this.equipmentRepository = equipmentRepository;
        this.equipmentEventRepository = equipmentEventRepository;
        this.minHistoryForWaitTime = minHistoryForWaitTime;
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }

    @Transactional(readOnly = true)
    public SessionUsageSummary getUserUsageSummary(User user, Duration window) {
        Instant since = Instant.now().minus(window);
        List<EquipmentSession> sessions =
                equipmentSessionRepository.findByUserAndStartedAtAfter(user, since);
        return buildSummary(since, sessions);
    }

    @Transactional(readOnly = true)
    public SessionUsageSummary getOverallUsageSummary(Duration window) {
        Instant since = Instant.now().minus(window);
        List<EquipmentSession> sessions = equipmentSessionRepository.findByStartedAtAfter(since);
        return buildSummary(since, sessions);
    }

    @Transactional(readOnly = true)
    public UserAnalyticsSummary getUserAnalyticsSummary(User user, Duration window) {
        Instant since = Instant.now().minus(window);
        List<EquipmentSession> sessions =
                equipmentSessionRepository.findByUserAndStartedAtAfter(user, since);
        return buildAnalyticsSummary(since, sessions);
    }

    @Transactional(readOnly = true)
    public SystemAnalyticsSummary getSystemAnalyticsSummary(Duration window) {
        Instant since = Instant.now().minus(window);
        List<EquipmentSession> sessions = equipmentSessionRepository.findByStartedAtAfter(since);
        UserAnalyticsSummary summary = buildAnalyticsSummary(since, sessions);

        return new SystemAnalyticsSummary(
                summary.getWindowStart(),
                summary.getTotalSessions(),
                summary.getCompletedSessions(),
                summary.getTimedOutSessions(),
                summary.getActiveSessions(),
                summary.getAverageDurationSeconds(),
                summary.getPeakStartHour(),
                summary.getPeakSessionCount(),
                summary.getMostUsedEquipmentId(),
                summary.getMostUsedEquipmentName(),
                summary.getMostUsedEquipmentCount(),
                summary.getTimeoutRate()
        );
    }

    @Transactional(readOnly = true)
    public DataQualityAudit getDataQualityAudit(Duration window) {
        Instant now = Instant.now();
        Instant since = now.minus(window);

        List<Long> missingEventSessionIds = equipmentSessionRepository.findSessionIdsMissingEvents(since);
        List<Long> missingEventSample = missingEventSessionIds.stream().limit(5).toList();

        List<EquipmentSessionStatus> endedStatuses = Arrays.asList(
                EquipmentSessionStatus.ENDED,
                EquipmentSessionStatus.TIMED_OUT
        );

        int activeWithEndedAt = (int) equipmentSessionRepository
                .countByStatusAndEndedAtIsNotNull(EquipmentSessionStatus.ACTIVE);
        int endedMissingEndedAt = (int) equipmentSessionRepository
                .countByStatusInAndEndedAtIsNull(endedStatuses);
        int endedMissingEndReason = (int) equipmentSessionRepository
                .countByStatusInAndEndReasonIsNull(endedStatuses);

        Instant cutoff = now.minus(Duration.ofMinutes(sessionTimeoutMinutes));
        List<EquipmentSession> staleActiveSessions =
                equipmentSessionRepository.findByStatusAndStartedAtBefore(EquipmentSessionStatus.ACTIVE, cutoff);
        List<Long> staleSessionIds = staleActiveSessions.stream()
                .map(EquipmentSession::getId)
                .filter(id -> id != null)
                .limit(5)
                .toList();

        List<EquipmentEvent> recentEvents = equipmentEventRepository.findByOccurredAtAfter(since);
        Map<Long, List<EquipmentEvent>> eventsBySession = new HashMap<>();
        for (EquipmentEvent event : recentEvents) {
            EquipmentSession session = event.getSession();
            if (session == null || session.getId() == null) {
                continue;
            }
            eventsBySession
                    .computeIfAbsent(session.getId(), key -> new ArrayList<>())
                    .add(event);
        }

        Set<Long> sessionIds = eventsBySession.keySet();
        Map<Long, EquipmentSessionStatus> sessionStatusById = new HashMap<>();
        if (!sessionIds.isEmpty()) {
            List<EquipmentSession> sessions = equipmentSessionRepository.findAllById(sessionIds);
            for (EquipmentSession session : sessions) {
                if (session.getId() != null) {
                    sessionStatusById.put(session.getId(), session.getStatus());
                }
            }
        }

        int duplicateStarts = 0;
        int duplicateTerminals = 0;
        int invalidTerminalEvents = 0;
        int outOfOrderEvents = 0;
        List<Long> duplicateStartSamples = new ArrayList<>();
        List<Long> duplicateTerminalSamples = new ArrayList<>();
        List<Long> invalidTerminalSamples = new ArrayList<>();
        List<Long> outOfOrderSamples = new ArrayList<>();

        for (Map.Entry<Long, List<EquipmentEvent>> entry : eventsBySession.entrySet()) {
            Long sessionId = entry.getKey();
            List<EquipmentEvent> events = entry.getValue();

            int startCount = 0;
            int endCount = 0;
            int timeoutCount = 0;
            Instant earliestStart = null;
            Instant earliestTerminal = null;
            EquipmentEvent lastEvent = null;

            for (EquipmentEvent event : events) {
                EquipmentEventType type = event.getEventType();
                if (type == EquipmentEventType.SESSION_STARTED) {
                    startCount++;
                    if (earliestStart == null || event.getOccurredAt().isBefore(earliestStart)) {
                        earliestStart = event.getOccurredAt();
                    }
                } else if (type == EquipmentEventType.SESSION_ENDED) {
                    endCount++;
                    if (earliestTerminal == null || event.getOccurredAt().isBefore(earliestTerminal)) {
                        earliestTerminal = event.getOccurredAt();
                    }
                } else if (type == EquipmentEventType.SESSION_TIMED_OUT) {
                    timeoutCount++;
                    if (earliestTerminal == null || event.getOccurredAt().isBefore(earliestTerminal)) {
                        earliestTerminal = event.getOccurredAt();
                    }
                }

                if (lastEvent == null
                        || event.getOccurredAt().isAfter(lastEvent.getOccurredAt())
                        || (event.getOccurredAt().equals(lastEvent.getOccurredAt())
                            && event.getId() != null
                            && lastEvent.getId() != null
                            && event.getId() > lastEvent.getId())) {
                    lastEvent = event;
                }
            }

            if (startCount > 1) {
                duplicateStarts++;
                if (duplicateStartSamples.size() < 5) {
                    duplicateStartSamples.add(sessionId);
                }
            }

            int terminalCount = endCount + timeoutCount;
            if (terminalCount > 1 || endCount > 1 || timeoutCount > 1) {
                duplicateTerminals++;
                if (duplicateTerminalSamples.size() < 5) {
                    duplicateTerminalSamples.add(sessionId);
                }
            }

            if (earliestStart != null && earliestTerminal != null && earliestTerminal.isBefore(earliestStart)) {
                outOfOrderEvents++;
                if (outOfOrderSamples.size() < 5) {
                    outOfOrderSamples.add(sessionId);
                }
            }

            EquipmentSessionStatus status = sessionStatusById.get(sessionId);
            if (status != null && lastEvent != null) {
                EquipmentEventType lastType = lastEvent.getEventType();
                boolean invalid = false;
                if (status == EquipmentSessionStatus.ACTIVE && lastType != EquipmentEventType.SESSION_STARTED) {
                    invalid = true;
                } else if (status == EquipmentSessionStatus.ENDED && lastType != EquipmentEventType.SESSION_ENDED) {
                    invalid = true;
                } else if (status == EquipmentSessionStatus.TIMED_OUT && lastType != EquipmentEventType.SESSION_TIMED_OUT) {
                    invalid = true;
                }

                if (invalid) {
                    invalidTerminalEvents++;
                    if (invalidTerminalSamples.size() < 5) {
                        invalidTerminalSamples.add(sessionId);
                    }
                }
            }
        }

        return new DataQualityAudit(
                since,
                missingEventSessionIds.size(),
                missingEventSample,
                activeWithEndedAt,
                endedMissingEndedAt,
                endedMissingEndReason,
                staleActiveSessions.size(),
                staleSessionIds,
                duplicateStarts,
                duplicateStartSamples,
                duplicateTerminals,
                duplicateTerminalSamples,
                invalidTerminalEvents,
                invalidTerminalSamples,
                outOfOrderEvents,
                outOfOrderSamples
        );
    }

    @Transactional(readOnly = true)
    public List<EquipmentUtilizationSummary> getUtilizationByEquipment(Duration window, ZoneId zoneId) {
        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minus(window);
        List<EquipmentSession> sessions =
                equipmentSessionRepository.findSessionsOverlappingWindow(windowStart, windowEnd);
        List<Equipment> equipmentList = equipmentRepository.findAll();

        Map<Long, List<EquipmentSession>> sessionsByEquipment = new HashMap<>();
        for (EquipmentSession session : sessions) {
            Equipment equipment = session.getEquipment();
            if (equipment == null) {
                continue;
            }
            sessionsByEquipment
                    .computeIfAbsent(equipment.getId(), k -> new ArrayList<>())
                    .add(session);
        }

        ZonedDateTime windowStartZ = windowStart.atZone(zoneId);
        ZonedDateTime windowEndZ = windowEnd.atZone(zoneId);
        ZonedDateTime bucketStart = windowStartZ.truncatedTo(ChronoUnit.HOURS);
        if (bucketStart.isAfter(windowStartZ)) {
            bucketStart = bucketStart.minusHours(1);
        }

        List<Instant> bucketStarts = new ArrayList<>();
        ZonedDateTime cursor = bucketStart;
        while (cursor.isBefore(windowEndZ)) {
            bucketStarts.add(cursor.toInstant());
            cursor = cursor.plusHours(1);
        }

        List<EquipmentUtilizationSummary> summaries = new ArrayList<>();
        for (Equipment equipment : equipmentList) {
            List<EquipmentSession> equipmentSessions =
                    sessionsByEquipment.getOrDefault(equipment.getId(), List.of());

            List<EquipmentUtilizationPoint> points = new ArrayList<>();
            for (Instant hourStart : bucketStarts) {
                Instant hourEnd = hourStart.plus(1, ChronoUnit.HOURS);
                long overlapSeconds = 0;

                for (EquipmentSession session : equipmentSessions) {
                    Instant sessionStart = session.getStartedAt();
                    Instant sessionEnd = session.getEndedAt() == null ? windowEnd : session.getEndedAt();

                    Instant overlapStart = maxInstant(sessionStart, hourStart, windowStart);
                    Instant overlapEnd = minInstant(sessionEnd, hourEnd, windowEnd);
                    if (overlapStart.isBefore(overlapEnd)) {
                        overlapSeconds += Duration.between(overlapStart, overlapEnd).getSeconds();
                    }
                }

                double utilization = Math.min(100d, (overlapSeconds / 3600d) * 100d);
                points.add(new EquipmentUtilizationPoint(hourStart, utilization));
            }

            summaries.add(new EquipmentUtilizationSummary(
                    equipment.getId(),
                    equipment.getCode(),
                    equipment.getName(),
                    points
            ));
        }

        return summaries;
    }

    @Transactional(readOnly = true)
    public List<EquipmentUtilizationSnapshot> getRollingUtilization(Duration window) {
        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minus(window);
        List<EquipmentSession> sessions =
                equipmentSessionRepository.findSessionsOverlappingWindow(windowStart, windowEnd);
        List<Equipment> equipmentList = equipmentRepository.findAll();

        Map<Long, List<EquipmentSession>> sessionsByEquipment = new HashMap<>();
        for (EquipmentSession session : sessions) {
            Equipment equipment = session.getEquipment();
            if (equipment == null) {
                continue;
            }
            sessionsByEquipment
                    .computeIfAbsent(equipment.getId(), k -> new ArrayList<>())
                    .add(session);
        }

        long windowSeconds = Math.max(1L, window.getSeconds());
        List<EquipmentUtilizationSnapshot> snapshots = new ArrayList<>();

        for (Equipment equipment : equipmentList) {
            List<EquipmentSession> equipmentSessions =
                    sessionsByEquipment.getOrDefault(equipment.getId(), List.of());

            long overlapSeconds = 0;
            for (EquipmentSession session : equipmentSessions) {
                Instant sessionStart = session.getStartedAt();
                Instant sessionEnd = session.getEndedAt() == null ? windowEnd : session.getEndedAt();

                Instant overlapStart = sessionStart.isAfter(windowStart) ? sessionStart : windowStart;
                Instant overlapEnd = sessionEnd.isBefore(windowEnd) ? sessionEnd : windowEnd;
                if (overlapStart.isBefore(overlapEnd)) {
                    overlapSeconds += Duration.between(overlapStart, overlapEnd).getSeconds();
                }
            }

            double utilization = Math.min(100d, (overlapSeconds / (double) windowSeconds) * 100d);
            snapshots.add(new EquipmentUtilizationSnapshot(
                    equipment.getId(),
                    equipment.getCode(),
                    equipment.getName(),
                    windowStart,
                    windowEnd,
                    utilization
            ));
        }

        return snapshots;
    }

    @Transactional(readOnly = true)
    public EquipmentWaitTimeEstimate getWaitTimeEstimate(Equipment equipment, Duration historyWindow) {
        EquipmentSession activeSession = equipmentSessionRepository
                .findByEquipmentAndStatus(equipment, EquipmentSessionStatus.ACTIVE)
                .orElse(null);

        boolean inUse = activeSession != null;
        Instant now = Instant.now();
        Long activeElapsedSeconds = null;
        Instant activeStartedAt = null;

        if (activeSession != null) {
            activeStartedAt = activeSession.getStartedAt();
            activeElapsedSeconds = Duration.between(activeStartedAt, now).getSeconds();
        }

        List<EquipmentSessionStatus> completedStatuses =
                Arrays.asList(EquipmentSessionStatus.ENDED, EquipmentSessionStatus.TIMED_OUT);
        Instant since = now.minus(historyWindow);
        List<EquipmentSession> historySessions = equipmentSessionRepository
                .findByEquipmentAndStatusInAndEndedAtAfter(equipment, completedStatuses, since);

        List<Long> durations = new ArrayList<>();
        for (EquipmentSession session : historySessions) {
            if (session.getEndedAt() != null) {
                durations.add(Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds());
            }
        }

        Long averageDurationSeconds = null;
        if (durations.size() >= minHistoryForWaitTime) {
            durations.sort(Long::compareTo);
            int mid = durations.size() / 2;
            if (durations.size() % 2 == 0) {
                averageDurationSeconds = (durations.get(mid - 1) + durations.get(mid)) / 2;
            } else {
                averageDurationSeconds = durations.get(mid);
            }
        }

        Long estimatedWaitSeconds = null;
        if (inUse && averageDurationSeconds != null && activeElapsedSeconds != null) {
            estimatedWaitSeconds = Math.max(0L, averageDurationSeconds - activeElapsedSeconds);
        } else if (!inUse) {
            estimatedWaitSeconds = 0L;
        }

        return new EquipmentWaitTimeEstimate(
                equipment.getId(),
                equipment.getCode(),
                equipment.getName(),
                inUse,
                estimatedWaitSeconds,
                averageDurationSeconds,
                activeElapsedSeconds,
                activeStartedAt
        );
    }

    @Transactional(readOnly = true)
    public Page<EquipmentEventDto> getEvents(
            Long equipmentId,
            Long userId,
            Long sessionId,
            EquipmentEventType eventType,
            Instant since,
            Instant until,
            @NonNull Pageable pageable
    ) {
        Specification<EquipmentEvent> spec = Specification.where(null);
        if (equipmentId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("equipment").get("id"), equipmentId));
        }
        if (userId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("user").get("id"), userId));
        }
        if (sessionId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("session").get("id"), sessionId));
        }
        if (eventType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("eventType"), eventType));
        }
        if (since != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), since));
        }
        if (until != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), until));
        }

        Page<EquipmentEvent> page = equipmentEventRepository.findAll(spec, pageable);

        return page.map(event -> new EquipmentEventDto(
                event.getId(),
                event.getEventType().name(),
                event.getEquipment().getId(),
                event.getSession().getId(),
                event.getUser().getId(),
                event.getOccurredAt(),
                event.getMetadata()
        ));
    }

    private Instant maxInstant(Instant a, Instant b, Instant c) {
        Instant max = a.isAfter(b) ? a : b;
        return max.isAfter(c) ? max : c;
    }

    private Instant minInstant(Instant a, Instant b, Instant c) {
        Instant min = a.isBefore(b) ? a : b;
        return min.isBefore(c) ? min : c;
    }

    private SessionUsageSummary buildSummary(Instant since, List<EquipmentSession> sessions) {
        int totalSessions = sessions.size();
        int completedSessions = 0;
        long durationSumSeconds = 0;

        Map<Integer, Integer> hourCounts = new HashMap<>();
        for (EquipmentSession session : sessions) {
            if (session.getEndedAt() != null) {
                completedSessions++;
                durationSumSeconds += Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds();
            }
            ZonedDateTime startTime = session.getStartedAt().atZone(ZoneId.systemDefault());
            int hour = startTime.getHour();
            hourCounts.put(hour, hourCounts.getOrDefault(hour, 0) + 1);
        }

        long averageDurationSeconds = completedSessions == 0 ? 0 : durationSumSeconds / completedSessions;
        Integer peakStartHour = null;
        int peakSessionCount = 0;

        for (Map.Entry<Integer, Integer> entry : hourCounts.entrySet()) {
            if (entry.getValue() > peakSessionCount) {
                peakSessionCount = entry.getValue();
                peakStartHour = entry.getKey();
            }
        }

        return new SessionUsageSummary(
                since,
                totalSessions,
                completedSessions,
                averageDurationSeconds,
                peakStartHour,
                peakSessionCount
        );
    }

    private UserAnalyticsSummary buildAnalyticsSummary(Instant since, List<EquipmentSession> sessions) {
        int totalSessions = sessions.size();
        int completedSessions = 0;
        int timedOutSessions = 0;
        int activeSessions = 0;
        long durationSumSeconds = 0;

        Map<Integer, Integer> hourCounts = new HashMap<>();
        Map<Long, Integer> equipmentCounts = new HashMap<>();
        Map<Long, String> equipmentNames = new HashMap<>();

        for (EquipmentSession session : sessions) {
            EquipmentSessionStatus status = session.getStatus();
            if (status == EquipmentSessionStatus.ACTIVE) {
                activeSessions++;
            }

            if (session.getEndedAt() != null) {
                completedSessions++;
                durationSumSeconds += Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds();
            }

            if (session.getEndReason() == EquipmentSessionEndReason.TIMEOUT
                    || status == EquipmentSessionStatus.TIMED_OUT) {
                timedOutSessions++;
            }

            Equipment equipment = session.getEquipment();
            if (equipment != null && equipment.getId() != null) {
                equipmentCounts.put(
                        equipment.getId(),
                        equipmentCounts.getOrDefault(equipment.getId(), 0) + 1
                );
                equipmentNames.putIfAbsent(equipment.getId(), equipment.getName());
            }

            ZonedDateTime startTime = session.getStartedAt().atZone(ZoneId.systemDefault());
            int hour = startTime.getHour();
            hourCounts.put(hour, hourCounts.getOrDefault(hour, 0) + 1);
        }

        long averageDurationSeconds = completedSessions == 0 ? 0 : durationSumSeconds / completedSessions;
        Integer peakStartHour = null;
        int peakSessionCount = 0;

        for (Map.Entry<Integer, Integer> entry : hourCounts.entrySet()) {
            if (entry.getValue() > peakSessionCount) {
                peakSessionCount = entry.getValue();
                peakStartHour = entry.getKey();
            }
        }

        Long mostUsedEquipmentId = null;
        String mostUsedEquipmentName = null;
        int mostUsedEquipmentCount = 0;

        for (Map.Entry<Long, Integer> entry : equipmentCounts.entrySet()) {
            if (entry.getValue() > mostUsedEquipmentCount) {
                mostUsedEquipmentCount = entry.getValue();
                mostUsedEquipmentId = entry.getKey();
                mostUsedEquipmentName = equipmentNames.get(entry.getKey());
            }
        }

        double timeoutRate = totalSessions == 0 ? 0d : timedOutSessions / (double) totalSessions;

        return new UserAnalyticsSummary(
                since,
                totalSessions,
                completedSessions,
                timedOutSessions,
                activeSessions,
                averageDurationSeconds,
                peakStartHour,
                peakSessionCount,
                mostUsedEquipmentId,
                mostUsedEquipmentName,
                mostUsedEquipmentCount,
                timeoutRate
        );
    }
}
