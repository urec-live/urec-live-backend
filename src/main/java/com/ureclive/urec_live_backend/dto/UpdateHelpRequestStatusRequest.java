package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.HelpRequestStatus;
import jakarta.validation.constraints.NotNull;

/** Body of PUT /api/admin/help-requests/{id}/status. Only ON_THE_WAY and TOO_BUSY are accepted. */
public class UpdateHelpRequestStatusRequest {

    @NotNull(message = "Status is required")
    private HelpRequestStatus status;

    public HelpRequestStatus getStatus() { return status; }
    public void setStatus(HelpRequestStatus status) { this.status = status; }
}
