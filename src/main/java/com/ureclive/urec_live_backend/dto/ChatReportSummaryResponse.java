package com.ureclive.urec_live_backend.dto;

/** Counts for the admin Reported Messages page and its sidebar badge. */
public class ChatReportSummaryResponse {
    /** Messages with at least one open report. */
    public long openMessages;
    public long openReports;

    public ChatReportSummaryResponse(long openMessages, long openReports) {
        this.openMessages = openMessages;
        this.openReports = openReports;
    }
}
