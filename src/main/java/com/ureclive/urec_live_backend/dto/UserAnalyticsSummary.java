package com.ureclive.urec_live_backend.dto;

import java.time.Instant;

public class UserAnalyticsSummary {
    private final Instant windowStart;
    private final int totalSessions;
    private final int completedSessions;
    private final int timedOutSessions;
    private final int activeSessions;
    private final long averageDurationSeconds;
    private final Integer peakStartHour;
    private final int peakSessionCount;
    private final Long mostUsedEquipmentId;
    private final String mostUsedEquipmentName;
    private final int mostUsedEquipmentCount;
    private final double timeoutRate;

    public UserAnalyticsSummary(
            Instant windowStart,
            int totalSessions,
            int completedSessions,
            int timedOutSessions,
            int activeSessions,
            long averageDurationSeconds,
            Integer peakStartHour,
            int peakSessionCount,
            Long mostUsedEquipmentId,
            String mostUsedEquipmentName,
            int mostUsedEquipmentCount,
            double timeoutRate
    ) {
        this.windowStart = windowStart;
        this.totalSessions = totalSessions;
        this.completedSessions = completedSessions;
        this.timedOutSessions = timedOutSessions;
        this.activeSessions = activeSessions;
        this.averageDurationSeconds = averageDurationSeconds;
        this.peakStartHour = peakStartHour;
        this.peakSessionCount = peakSessionCount;
        this.mostUsedEquipmentId = mostUsedEquipmentId;
        this.mostUsedEquipmentName = mostUsedEquipmentName;
        this.mostUsedEquipmentCount = mostUsedEquipmentCount;
        this.timeoutRate = timeoutRate;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public int getTotalSessions() {
        return totalSessions;
    }

    public int getCompletedSessions() {
        return completedSessions;
    }

    public int getTimedOutSessions() {
        return timedOutSessions;
    }

    public int getActiveSessions() {
        return activeSessions;
    }

    public long getAverageDurationSeconds() {
        return averageDurationSeconds;
    }

    public Integer getPeakStartHour() {
        return peakStartHour;
    }

    public int getPeakSessionCount() {
        return peakSessionCount;
    }

    public Long getMostUsedEquipmentId() {
        return mostUsedEquipmentId;
    }

    public String getMostUsedEquipmentName() {
        return mostUsedEquipmentName;
    }

    public int getMostUsedEquipmentCount() {
        return mostUsedEquipmentCount;
    }

    public double getTimeoutRate() {
        return timeoutRate;
    }
}
