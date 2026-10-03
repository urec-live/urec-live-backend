package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.CreateIssueReportRequest;
import com.ureclive.urec_live_backend.dto.IssueReportResponse;
import com.ureclive.urec_live_backend.dto.MachineIssueStatusResponse;
import com.ureclive.urec_live_backend.service.EquipmentIssueService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Member-facing issue reporting. Deliberately not under /api/machines/**, which is
 * permitAll in SecurityConfig — every endpoint here requires an authenticated user.
 */
@RestController
@RequestMapping("/api/equipment-issues")
@CrossOrigin(origins = "*")
public class EquipmentIssueController {

    private final EquipmentIssueService issueService;

    @Autowired
    public EquipmentIssueController(EquipmentIssueService issueService) {
        this.issueService = issueService;
    }

    /** POST /api/equipment-issues — report a problem with a machine */
    @PostMapping
    public ResponseEntity<IssueReportResponse> createReport(
            @Valid @RequestBody CreateIssueReportRequest request,
            Authentication auth) {
        IssueReportResponse response = issueService.createReport(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** GET /api/equipment-issues/me — the current user's reports, newest first */
    @GetMapping("/me")
    public ResponseEntity<List<IssueReportResponse>> getMyReports(Authentication auth) {
        return ResponseEntity.ok(issueService.getMyReports(auth.getName()));
    }

    /** GET /api/equipment-issues/equipment/{equipmentId} — open-issue summary for the machine page */
    @GetMapping("/equipment/{equipmentId}")
    public ResponseEntity<MachineIssueStatusResponse> getMachineIssueStatus(@PathVariable Long equipmentId) {
        return ResponseEntity.ok(issueService.getMachineIssueStatus(equipmentId));
    }
}
