package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.CreateIssueReportRequest;
import com.ureclive.urec_live_backend.dto.IssueReportResponse;
import com.ureclive.urec_live_backend.dto.MachineIssueStatusResponse;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentIssueReport;
import com.ureclive.urec_live_backend.entity.IssueSeverity;
import com.ureclive.urec_live_backend.entity.IssueStatus;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentIssueReportRepository;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/** Member-facing side of equipment issue reporting. */
@Service
public class EquipmentIssueService {

    private final EquipmentIssueReportRepository issueReportRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    @Autowired
    public EquipmentIssueService(EquipmentIssueReportRepository issueReportRepository,
                                 EquipmentRepository equipmentRepository,
                                 UserRepository userRepository,
                                 ActivityLogService activityLogService) {
        this.issueReportRepository = issueReportRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
    }

    /**
     * Files a new issue report. A member can have only one unresolved report per machine,
     * so repeat reports (and accidental double-submits) are rejected with 409.
     */
    public IssueReportResponse createReport(CreateIssueReportRequest request, String username) {
        User reporter = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));

        Equipment equipment = equipmentRepository.findById(request.getEquipmentId())
                .filter(e -> !e.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Equipment not found with id: " + request.getEquipmentId()));

        // @Size counts surrounding whitespace, so re-check once trimmed
        String description = request.getDescription().trim();
        if (description.length() < CreateIssueReportRequest.MIN_DESCRIPTION_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Description must be at least " + CreateIssueReportRequest.MIN_DESCRIPTION_LENGTH + " characters");
        }

        if (issueReportRepository.existsByReporterAndEquipmentAndStatusNot(reporter, equipment, IssueStatus.RESOLVED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have an open report for this machine");
        }

        EquipmentIssueReport report = issueReportRepository.save(
                new EquipmentIssueReport(equipment, reporter, request.getSeverity(), description));

        String severityText = request.getSeverity() == IssueSeverity.OUT_OF_ORDER ? "not working" : "damaged";
        activityLogService.log("ISSUE_REPORTED", username,
                "Reported " + equipment.getName() + " as " + severityText,
                equipment.getName());

        return IssueReportResponse.from(report);
    }

    /** The member's own reports, newest first. */
    public List<IssueReportResponse> getMyReports(String username) {
        User reporter = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
        return issueReportRepository.findByReporterOrderByReportedAtDesc(reporter).stream()
                .map(IssueReportResponse::from)
                .collect(Collectors.toList());
    }

    /** Open-issue summary for a machine; openReportCount is 0 when nothing is open. */
    public MachineIssueStatusResponse getMachineIssueStatus(Long equipmentId) {
        List<EquipmentIssueReport> openReports = issueReportRepository
                .findByEquipmentIdAndStatusNotOrderByReportedAtDesc(equipmentId, IssueStatus.RESOLVED);
        return MachineIssueStatusResponse.from(equipmentId, openReports);
    }
}
