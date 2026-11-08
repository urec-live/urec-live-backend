package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.ChatMessageReport;
import com.ureclive.urec_live_backend.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ReportMessageRequest {

    @NotNull(message = "Reason is required")
    private ReportReason reason;

    @Size(max = ChatMessageReport.MAX_NOTE_LENGTH, message = "Note must be at most 300 characters")
    private String note;

    public ReportReason getReason() { return reason; }
    public void setReason(ReportReason reason) { this.reason = reason; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
