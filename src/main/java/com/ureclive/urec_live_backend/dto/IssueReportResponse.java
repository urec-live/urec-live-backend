package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.EquipmentIssueReport;
import com.ureclive.urec_live_backend.entity.IssueSeverity;
import com.ureclive.urec_live_backend.entity.IssueStatus;

import java.time.Instant;

public class IssueReportResponse {

    private Long id;
    private Long equipmentId;
    private String equipmentName;
    private String equipmentCode;
    private IssueSeverity severity;
    private String description;
    private IssueStatus status;
    private String reporterUsername;
    private Instant reportedAt;
    private Instant updatedAt;
    private Instant resolvedAt;

    public IssueReportResponse() {}

    public static IssueReportResponse from(EquipmentIssueReport report) {
        IssueReportResponse dto = new IssueReportResponse();
        dto.id = report.getId();
        dto.equipmentId = report.getEquipment().getId();
        dto.equipmentName = report.getEquipment().getName();
        dto.equipmentCode = report.getEquipment().getCode();
        dto.severity = report.getSeverity();
        dto.description = report.getDescription();
        dto.status = report.getStatus();
        dto.reporterUsername = report.getReporter().getUsername();
        dto.reportedAt = report.getReportedAt();
        dto.updatedAt = report.getUpdatedAt();
        dto.resolvedAt = report.getResolvedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Long getEquipmentId() { return equipmentId; }
    public String getEquipmentName() { return equipmentName; }
    public String getEquipmentCode() { return equipmentCode; }
    public IssueSeverity getSeverity() { return severity; }
    public String getDescription() { return description; }
    public IssueStatus getStatus() { return status; }
    public String getReporterUsername() { return reporterUsername; }
    public Instant getReportedAt() { return reportedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
