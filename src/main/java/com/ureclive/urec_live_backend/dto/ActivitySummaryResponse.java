package com.ureclive.urec_live_backend.dto;

public class ActivitySummaryResponse {
    private long checkInsToday;
    private long checkOutsToday;
    private long sessionsToday;
    private long registrationsToday;

    public ActivitySummaryResponse(long checkInsToday, long checkOutsToday,
                                   long sessionsToday, long registrationsToday) {
        this.checkInsToday = checkInsToday;
        this.checkOutsToday = checkOutsToday;
        this.sessionsToday = sessionsToday;
        this.registrationsToday = registrationsToday;
    }

    public long getCheckInsToday() { return checkInsToday; }
    public long getCheckOutsToday() { return checkOutsToday; }
    public long getSessionsToday() { return sessionsToday; }
    public long getRegistrationsToday() { return registrationsToday; }
}