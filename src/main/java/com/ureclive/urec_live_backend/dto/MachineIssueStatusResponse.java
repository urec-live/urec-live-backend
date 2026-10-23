package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.EquipmentIssueReport;
import com.ureclive.urec_live_backend.entity.IssueSeverity;
import com.ureclive.urec_live_backend.entity.IssueStatus;

import java.util.Comparator;
import java.util.List;

/**
 * Open-issue summary for one machine, shown to every member on the machine page.
 * Deliberately carries no reporter names or descriptions.
 */
public class MachineIssueStatusResponse {

    private Long equipmentId;
    private long openReportCount;
    private IssueSeverity worstSeverity;  // null when nothing is open
    private IssueStatus status;           // furthest-along open status; null when nothing is open

    public MachineIssueStatusResponse() {}

    public static MachineIssueStatusResponse from(Long equipmentId, List<EquipmentIssueReport> openReports) {
        MachineIssueStatusResponse dto = new MachineIssueStatusResponse();
        dto.equipmentId = equipmentId;
        dto.openReportCount = openReports.size();
        dto.worstSeverity = openReports.stream()
                .map(EquipmentIssueReport::getSeverity)
                .min(Comparator.naturalOrder())
                .orElse(null);
        dto.status = openReports.stream()
                .map(EquipmentIssueReport::getStatus)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return dto;
    }

    public Long getEquipmentId() { return equipmentId; }
    public long getOpenReportCount() { return openReportCount; }
    public IssueSeverity getWorstSeverity() { return worstSeverity; }
    public IssueStatus getStatus() { return status; }
}
