package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.SessionUsageSummary;
import com.ureclive.urec_live_backend.entity.EquipmentSession;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {
    private final EquipmentSessionRepository equipmentSessionRepository;

    public AnalyticsService(EquipmentSessionRepository equipmentSessionRepository) {
        this.equipmentSessionRepository = equipmentSessionRepository;
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
}
