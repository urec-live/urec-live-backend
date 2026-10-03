package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.AdminEquipmentResponse;
import com.ureclive.urec_live_backend.dto.EquipmentIssueGroupResponse;
import com.ureclive.urec_live_backend.dto.EquipmentIssueSummaryResponse;
import com.ureclive.urec_live_backend.dto.IssueReportResponse;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentIssueReport;
import com.ureclive.urec_live_backend.entity.EquipmentStatuses;
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

/**
 * Admin side of equipment issue reporting: the grouped issues view, report status changes and taking
 * machines out of service. Resolving a machine's last open report puts it back in service.
 */
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
    private final MachineStatusService machineStatusService;

    @Autowired
    public AdminEquipmentIssueService(EquipmentIssueReportRepository issueReportRepository,
                                      EquipmentRepository equipmentRepository,
                                      ActivityLogService activityLogService,
                                      MachineStatusService machineStatusService) {
        this.issueReportRepository = issueReportRepository;
        this.equipmentRepository = equipmentRepository;
        this.activityLogService = activityLogService;
        this.machineStatusService = machineStatusService;
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
            if (status == IssueStatus.RESOLVED) returnToServiceIfFixed(report.getEquipment(), adminUsername);
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
            if (status == IssueStatus.RESOLVED) equipment = returnToServiceIfFixed(equipment, adminUsername);
        }
        return EquipmentIssueGroupResponse.from(equipment, openReports);
    }

    /**
     * Takes a machine out of service, or puts it back. Putting it back only affects a machine that's
     * currently out of order, so it never turns an in-use machine into an available one.
     */
    @Transactional
    public AdminEquipmentResponse setOutOfOrder(Long equipmentId, boolean outOfOrder, String adminUsername) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .filter(e -> !e.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Equipment not found with id: " + equipmentId));

        boolean currentlyOutOfOrder = EquipmentStatuses.isOutOfOrder(equipment.getStatus());
        if (outOfOrder && !currentlyOutOfOrder) {
            equipment = machineStatusService.changeStatus(equipment, EquipmentStatuses.OUT_OF_ORDER, adminUsername, null);
        } else if (!outOfOrder && currentlyOutOfOrder) {
            equipment = machineStatusService.changeStatus(equipment, EquipmentStatuses.AVAILABLE, adminUsername, null);
        }
        return AdminEquipmentResponse.from(equipment);
    }

    public EquipmentIssueSummaryResponse getSummary() {
        return new EquipmentIssueSummaryResponse(
                issueReportRepository.countByStatusAndEquipmentDeletedFalse(IssueStatus.REPORTED),
                issueReportRepository.countByStatusAndEquipmentDeletedFalse(IssueStatus.ACKNOWLEDGED),
                issueReportRepository.countByStatusAndEquipmentDeletedFalse(IssueStatus.IN_PROGRESS),
                issueReportRepository.countDistinctEquipmentByStatusNot(IssueStatus.RESOLVED),
                equipmentRepository.countByDeletedFalseAndStatusIgnoreCase(EquipmentStatuses.OUT_OF_ORDER));
    }

    /** Puts an out-of-order machine back in service once its last open report is resolved. */
    private Equipment returnToServiceIfFixed(Equipment equipment, String adminUsername) {
        if (EquipmentStatuses.isOutOfOrder(equipment.getStatus())
                && !issueReportRepository.existsByEquipmentIdAndStatusNot(equipment.getId(), IssueStatus.RESOLVED)) {
            return machineStatusService.changeStatus(equipment, EquipmentStatuses.AVAILABLE, adminUsername,
                    "last open report resolved");
        }
        return equipment;
    }

    private void logStatusChange(String adminUsername, Equipment equipment, String subject, IssueStatus status) {
        String statusText = status.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        activityLogService.log("ISSUE_STATUS_CHANGED", adminUsername,
                subject + " on " + equipment.getName() + " marked " + statusText,
                equipment.getName());
    }
}
