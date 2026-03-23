package com.ureclive.urec_live_backend.dto;

import java.util.List;

public class PeakHoursResponse {
    private String period;
    private List<HourlyCount> peakHours;

    public PeakHoursResponse(String period, List<HourlyCount> peakHours) {
        this.period = period;
        this.peakHours = peakHours;
    }

    public String getPeriod() { return period; }
    public List<HourlyCount> getPeakHours() { return peakHours; }

    public static class HourlyCount {
        private int hour;
        private long count;

        public HourlyCount(int hour, long count) {
            this.hour = hour;
            this.count = count;
        }

        public int getHour() { return hour; }
        public long getCount() { return count; }
    }
}
