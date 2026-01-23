package com.ureclive.urec_live_backend.dto;

import java.time.Instant;

public class SessionUsageSummary {
    private Instant since;
    private int totalSessions;
    private int completedSessions;
    private long averageDurationSeconds;
    private Integer peakStartHour;
    private int peakSessionCount;

    public SessionUsageSummary() {}

    public SessionUsageSummary(
            Instant since,
            int totalSessions,
            int completedSessions,
            long averageDurationSeconds,
            Integer peakStartHour,
            int peakSessionCount
    ) {
        this.since = since;
        this.totalSessions = totalSessions;
        this.completedSessions = completedSessions;
        this.averageDurationSeconds = averageDurationSeconds;
        this.peakStartHour = peakStartHour;
        this.peakSessionCount = peakSessionCount;
    }

    public Instant getSince() {
        return since;
    }

    public void setSince(Instant since) {
        this.since = since;
    }

    public int getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(int totalSessions) {
        this.totalSessions = totalSessions;
    }

    public int getCompletedSessions() {
        return completedSessions;
    }

    public void setCompletedSessions(int completedSessions) {
        this.completedSessions = completedSessions;
    }

    public long getAverageDurationSeconds() {
        return averageDurationSeconds;
    }

    public void setAverageDurationSeconds(long averageDurationSeconds) {
        this.averageDurationSeconds = averageDurationSeconds;
    }

    public Integer getPeakStartHour() {
        return peakStartHour;
    }

    public void setPeakStartHour(Integer peakStartHour) {
        this.peakStartHour = peakStartHour;
    }

    public int getPeakSessionCount() {
        return peakSessionCount;
    }

    public void setPeakSessionCount(int peakSessionCount) {
        this.peakSessionCount = peakSessionCount;
    }
}
