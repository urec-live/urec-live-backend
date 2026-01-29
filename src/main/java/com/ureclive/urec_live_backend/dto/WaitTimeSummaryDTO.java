package com.ureclive.urec_live_backend.dto;

public class WaitTimeSummaryDTO {
    private Double averageWaitMinutes;
    private int busyCount;
    private String longestWaitMachineName;
    private Long longestWaitSeconds;

    public WaitTimeSummaryDTO(Double averageWaitMinutes, int busyCount, String longestWaitMachineName,
            Long longestWaitSeconds) {
        this.averageWaitMinutes = averageWaitMinutes;
        this.busyCount = busyCount;
        this.longestWaitMachineName = longestWaitMachineName;
        this.longestWaitSeconds = longestWaitSeconds;
    }

    public Double getAverageWaitMinutes() {
        return averageWaitMinutes;
    }

    public void setAverageWaitMinutes(Double averageWaitMinutes) {
        this.averageWaitMinutes = averageWaitMinutes;
    }

    public int getBusyCount() {
        return busyCount;
    }

    public void setBusyCount(int busyCount) {
        this.busyCount = busyCount;
    }

    public String getLongestWaitMachineName() {
        return longestWaitMachineName;
    }

    public void setLongestWaitMachineName(String longestWaitMachineName) {
        this.longestWaitMachineName = longestWaitMachineName;
    }

    public Long getLongestWaitSeconds() {
        return longestWaitSeconds;
    }

    public void setLongestWaitSeconds(Long longestWaitSeconds) {
        this.longestWaitSeconds = longestWaitSeconds;
    }
}
