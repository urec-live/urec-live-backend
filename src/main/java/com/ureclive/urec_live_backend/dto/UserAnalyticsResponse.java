package com.ureclive.urec_live_backend.dto;

import java.util.Map;

public class UserAnalyticsResponse {
    private String period;
    private long totalActiveUsers;
    private long totalNewRegistrations;
    /** date string (yyyy-MM-dd) → distinct active user count */
    private Map<String, Long> dauByDate;
    /** date string (yyyy-MM-dd) → new registration count */
    private Map<String, Long> newRegistrationsByDate;

    public UserAnalyticsResponse(String period, long totalActiveUsers, long totalNewRegistrations,
                                  Map<String, Long> dauByDate, Map<String, Long> newRegistrationsByDate) {
        this.period = period;
        this.totalActiveUsers = totalActiveUsers;
        this.totalNewRegistrations = totalNewRegistrations;
        this.dauByDate = dauByDate;
        this.newRegistrationsByDate = newRegistrationsByDate;
    }

    public String getPeriod() { return period; }
    public long getTotalActiveUsers() { return totalActiveUsers; }
    public long getTotalNewRegistrations() { return totalNewRegistrations; }
    public Map<String, Long> getDauByDate() { return dauByDate; }
    public Map<String, Long> getNewRegistrationsByDate() { return newRegistrationsByDate; }
}
