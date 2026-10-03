package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentIssueReport;
import com.ureclive.urec_live_backend.entity.IssueSeverity;
import com.ureclive.urec_live_backend.entity.IssueStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** One machine on the admin Equipment Issues page, together with its reports. */
public class EquipmentIssueGroupResponse {

    private Long equipmentId;
    private String equipmentName;
    private String equipmentCode;
    private long openReportCount;
    private IssueSeverity worstSeverity;   // across open reports; null when every report is resolved
    private Instant latestReportedAt;      // newest open report, or newest report when all are resolved
    private List<IssueReportResponse> reports;

    public EquipmentIssueGroupResponse() {}

    /** {@code reports} must be non-empty, belong to {@code equipment}, and be ordered newest first. */
    public static EquipmentIssueGroupResponse from(Equipment equipment, List<EquipmentIssueReport> reports) {
        List<EquipmentIssueReport> open = reports.stream()
                .filter(r -> r.getStatus() != IssueStatus.RESOLVED)
                .collect(Collectors.toList());

        EquipmentIssueGroupResponse dto = new EquipmentIssueGroupResponse();
        dto.equipmentId = equipment.getId();
        dto.equipmentName = equipment.getName();
        dto.equipmentCode = equipment.getCode();
        dto.openReportCount = open.size();
        dto.worstSeverity = open.stream()
                .map(EquipmentIssueReport::getSeverity)
                .min(Comparator.naturalOrder())
                .orElse(null);
        dto.latestReportedAt = (open.isEmpty() ? reports : open).get(0).getReportedAt();
        dto.reports = reports.stream()
                .map(IssueReportResponse::from)
                .collect(Collectors.toList());
        return dto;
    }

    public Long getEquipmentId() { return equipmentId; }
    public String getEquipmentName() { return equipmentName; }
    public String getEquipmentCode() { return equipmentCode; }
    public long getOpenReportCount() { return openReportCount; }
    public IssueSeverity getWorstSeverity() { return worstSeverity; }
    public Instant getLatestReportedAt() { return latestReportedAt; }
    public List<IssueReportResponse> getReports() { return reports; }
}
