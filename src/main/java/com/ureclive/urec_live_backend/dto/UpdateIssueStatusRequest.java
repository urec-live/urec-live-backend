package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.IssueStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateIssueStatusRequest {

    @NotNull(message = "Status is required")
    private IssueStatus status;

    public IssueStatus getStatus() { return status; }
    public void setStatus(IssueStatus status) { this.status = status; }
}
