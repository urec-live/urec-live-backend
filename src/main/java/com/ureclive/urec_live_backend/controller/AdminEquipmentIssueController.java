package com.ureclive.urec_live_backend.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ureclive.urec_live_backend.dto.EquipmentIssueGroupResponse;
import com.ureclive.urec_live_backend.dto.EquipmentIssueSummaryResponse;
import com.ureclive.urec_live_backend.dto.IssueReportResponse;
import com.ureclive.urec_live_backend.dto.UpdateIssueStatusRequest;
import com.ureclive.urec_live_backend.service.AdminEquipmentIssueService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/equipment-issues")
@CrossOrigin(origins = "*")
// Role names are stored as-is (the admin UI assigns "ADMIN"), so accept both forms like AdminUserController
@PreAuthorize("hasAuthority('ADMIN') or hasAuthority('ROLE_ADMIN')")
public class AdminEquipmentIssueController {

    private static final Logger logger = LoggerFactory.getLogger(AdminEquipmentIssueController.class);

    private final AdminEquipmentIssueService adminIssueService;

    @Autowired
    public AdminEquipmentIssueController(AdminEquipmentIssueService adminIssueService) {
        this.adminIssueService = adminIssueService;
    }

    /** GET /api/admin/equipment-issues?includeResolved=false — reports grouped by machine */
    @GetMapping
    public List<EquipmentIssueGroupResponse> getGrouped(
            @RequestParam(defaultValue = "false") boolean includeResolved) {
        logger.info("[GET /api/admin/equipment-issues] includeResolved={}", includeResolved);
        return adminIssueService.getGrouped(includeResolved);
    }

    /** GET /api/admin/equipment-issues/summary — counts for stat cards and the sidebar badge (polled; not logged) */
    @GetMapping("/summary")
    public EquipmentIssueSummaryResponse getSummary() {
        return adminIssueService.getSummary();
    }

    /** PUT /api/admin/equipment-issues/{id}/status — set one report's status */
    @PutMapping("/{id}/status")
    public IssueReportResponse updateStatus(@PathVariable Long id,
                                            @Valid @RequestBody UpdateIssueStatusRequest request,
                                            Authentication auth) {
        logger.info("[PUT /api/admin/equipment-issues/{}/status] status={}", id, request.getStatus());
        return adminIssueService.updateStatus(id, request.getStatus(), auth.getName());
    }

    /** PUT /api/admin/equipment-issues/equipment/{equipmentId}/status — set status on all of a machine's open reports */
    @PutMapping("/equipment/{equipmentId}/status")
    public EquipmentIssueGroupResponse updateStatusForEquipment(@PathVariable Long equipmentId,
                                                                @Valid @RequestBody UpdateIssueStatusRequest request,
                                                                Authentication auth) {
        logger.info("[PUT /api/admin/equipment-issues/equipment/{}/status] status={}", equipmentId, request.getStatus());
        return adminIssueService.updateStatusForEquipment(equipmentId, request.getStatus(), auth.getName());
    }
}
