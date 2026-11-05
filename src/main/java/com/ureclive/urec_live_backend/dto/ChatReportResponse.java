package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.ChatMessageReport;

/** One member's report of a chat message. */
public class ChatReportResponse {
    public Long id;
    public String reporterUsername;
    public String reason;
    public String note;
    public String status;
    public String reportedAt;
    public String resolvedAt;
    public String resolvedBy;

    public static ChatReportResponse from(ChatMessageReport report) {
        ChatReportResponse r = new ChatReportResponse();
        r.id = report.getId();
        r.reporterUsername = report.getReporterUsername();
        r.reason = report.getReason().name();
        r.note = report.getNote();
        r.status = report.getStatus().name();
        r.reportedAt = report.getReportedAt().toString();
        r.resolvedAt = report.getResolvedAt() == null ? null : report.getResolvedAt().toString();
        r.resolvedBy = report.getResolvedBy();
        return r;
    }
}
