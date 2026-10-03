package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.EquipmentIssueGroupResponse;
import com.ureclive.urec_live_backend.dto.EquipmentIssueSummaryResponse;
import com.ureclive.urec_live_backend.dto.IssueReportResponse;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentIssueReport;
import com.ureclive.urec_live_backend.entity.IssueStatus;
import com.ureclive.urec_live_backend.repository.EquipmentIssueReportRepository;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Admin side of equipment issue reporting: the grouped issues view and status changes. */
@Service
public class AdminEquipmentIssueService {

    /** Machines with open reports first (worst severity first), then most recent report first. */
    private static final Comparator<EquipmentIssueGroupResponse> GROUP_ORDER =
            Comparator.comparing(EquipmentIssueGroupResponse::getWorstSeverity,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(EquipmentIssueGroupResponse::getLatestReportedAt,
                            Comparator.nullsLast(Comparator.reverseOrder()));

    private final EquipmentIssueReportRepository issueReportRepository;
    private final EquipmentRepository equipmentRepository;
    private final ActivityLogService activityLogService;

    @Autowired
    public AdminEquipmentIssueService(EquipmentIssueReportRepository issueReportRepository,
                                      EquipmentRepository equipmentRepository,
                                      ActivityLogService activityLogService) {
        this.issueReportRepository = issueReportRepository;
        this.equipmentRepository = equipmentRepository;
        this.activityLogService = activityLogService;
    }

    /**
     * Reports grouped by machine. Resolved reports are only included when asked for;
     * reports on removed machines are never included.
     */
    @Transactional(readOnly = true)
    public List<EquipmentIssueGroupResponse> getGrouped(boolean includeResolved) {
        List<EquipmentIssueReport> reports = includeResolved
                ? issueReportRepository.findByEquipmentDeletedFalseOrderByReportedAtDesc()
                : issueReportRepository.findByStatusNotAndEquipmentDeletedFalseOrderByReportedAtDesc(IssueStatus.RESOLVED);

        // Reports arrive newest first, and groupingBy keeps encounter order within each group
        Map<Long, List<EquipmentIssueReport>> byEquipment = reports.stream()
                .collect(Collectors.groupingBy(r -> r.getEquipment().getId(),
                        LinkedHashMap::new, Collectors.toList()));

        return byEquipment.values().stream()
                .map(group -> EquipmentIssueGroupResponse.from(group.get(0).getEquipment(), group))
                .sorted(GROUP_ORDER)
                .collect(Collectors.toList());
    }

    @Transactional
    public IssueReportResponse updateStatus(Long reportId, IssueStatus status, String adminUsername) {
        EquipmentIssueReport report = issueReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Issue report not found with id: " + reportId));

        if (report.changeStatus(status)) {
            report = issueReportRepository.save(report);
            logStatusChange(adminUsername, report.getEquipment(), "Report #" + report.getId(), status);
        }
        return IssueReportResponse.from(report);
    }

    /** Applies {@code status} to every open report on the machine. */
    @Transactional
    public EquipmentIssueGroupResponse updateStatusForEquipment(Long equipmentId, IssueStatus status, String adminUsername) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Equipment not found with id: " + equipmentId));

        List<EquipmentIssueReport> openReports = issueReportRepository
                .findByEquipmentIdAndStatusNotOrderByReportedAtDesc(equipmentId, IssueStatus.RESOLVED);
        if (openReports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No open issue reports for equipment id: " + equipmentId);
        }

        List<EquipmentIssueReport> changed = openReports.stream()
                .filter(r -> r.changeStatus(status))
                .collect(Collectors.toList());
        if (!changed.isEmpty()) {
            issueReportRepository.saveAll(changed);
            logStatusChange(adminUsername, equipment, changed.size() + " report(s)", status);
        }
        return EquipmentIssueGroupResponse.from(equipment, openReports);
    }

    public EquipmentIssueSummaryResponse getSummary() {
        return new EquipmentIssueSummaryResponse(
                issueReportRepository.countByStatusAndEquipmentDeletedFalse(IssueStatus.REPORTED),
                issueReportRepository.countByStatusAndEquipmentDeletedFalse(IssueStatus.ACKNOWLEDGED),
                issueReportRepository.countByStatusAndEquipmentDeletedFalse(IssueStatus.IN_PROGRESS),
                issueReportRepository.countDistinctEquipmentByStatusNot(IssueStatus.RESOLVED));
    }

    private void logStatusChange(String adminUsername, Equipment equipment, String subject, IssueStatus status) {
        String statusText = status.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        activityLogService.log("ISSUE_STATUS_CHANGED", adminUsername,
                subject + " on " + equipment.getName() + " marked " + statusText,
                equipment.getName());
    }
}
