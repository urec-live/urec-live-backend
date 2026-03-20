package com.ureclive.urec_live_backend.dto;

import java.util.List;

public class UsageStatsResponse {
    private String period;
    private long totalSessions;
    private List<MachineUsage> mostUsed;
    private List<MachineUsage> leastUsed;

    public UsageStatsResponse(String period, long totalSessions,
                               List<MachineUsage> mostUsed, List<MachineUsage> leastUsed) {
        this.period = period;
        this.totalSessions = totalSessions;
        this.mostUsed = mostUsed;
        this.leastUsed = leastUsed;
    }

    public String getPeriod() { return period; }
    public long getTotalSessions() { return totalSessions; }
    public List<MachineUsage> getMostUsed() { return mostUsed; }
    public List<MachineUsage> getLeastUsed() { return leastUsed; }

    public static class MachineUsage {
        private Long machineId;
        private String machineName;
        private long sessionCount;
        private long avgDurationSeconds;

        public MachineUsage(Long machineId, String machineName, long sessionCount, long avgDurationSeconds) {
            this.machineId = machineId;
            this.machineName = machineName;
            this.sessionCount = sessionCount;
            this.avgDurationSeconds = avgDurationSeconds;
        }

        public Long getMachineId() { return machineId; }
        public String getMachineName() { return machineName; }
        public long getSessionCount() { return sessionCount; }
        public long getAvgDurationSeconds() { return avgDurationSeconds; }
    }
}
