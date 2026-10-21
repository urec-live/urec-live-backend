package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.IssueSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateIssueReportRequest {

    public static final int MIN_DESCRIPTION_LENGTH = 10;
    public static final int MAX_DESCRIPTION_LENGTH = 1000;

    @NotNull(message = "Equipment is required")
    private Long equipmentId;

    @NotNull(message = "Severity is required")
    private IssueSeverity severity;

    @NotBlank(message = "Description is required")
    @Size(min = MIN_DESCRIPTION_LENGTH, max = MAX_DESCRIPTION_LENGTH,
          message = "Description must be between 10 and 1000 characters")
    private String description;

    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }

    public IssueSeverity getSeverity() { return severity; }
    public void setSeverity(IssueSeverity severity) { this.severity = severity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
