package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.AdminHelpRequestResponse;
import com.ureclive.urec_live_backend.dto.UpdateHelpRequestStatusRequest;
import com.ureclive.urec_live_backend.service.AdminHelpRequestService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/help-requests")
@CrossOrigin(origins = "*")
// Role names are stored as-is (the admin UI assigns "ADMIN"), so accept both forms like AdminUserController
@PreAuthorize("hasAuthority('ADMIN') or hasAuthority('ROLE_ADMIN')")
public class AdminHelpRequestController {

    private static final Logger logger = LoggerFactory.getLogger(AdminHelpRequestController.class);

    private final AdminHelpRequestService adminHelpRequestService;

    @Autowired
    public AdminHelpRequestController(AdminHelpRequestService adminHelpRequestService) {
        this.adminHelpRequestService = adminHelpRequestService;
    }

    /** GET /api/admin/help-requests — open requests, longest-waiting first (polled; not logged) */
    @GetMapping
    public List<AdminHelpRequestResponse> getOpen() {
        return adminHelpRequestService.getOpen();
    }

    /** GET /api/admin/help-requests/history?limit=50 — recently closed requests, newest first */
    @GetMapping("/history")
    public List<AdminHelpRequestResponse> getHistory(@RequestParam(defaultValue = "50") int limit) {
        return adminHelpRequestService.getHistory(limit);
    }

    /** PUT /api/admin/help-requests/{id}/status — "On the way" or "Too busy" */
    @PutMapping("/{id}/status")
    public AdminHelpRequestResponse setStatus(@PathVariable Long id,
                                              @Valid @RequestBody UpdateHelpRequestStatusRequest request,
                                              Authentication auth) {
        logger.info("[PUT /api/admin/help-requests/{}/status] status={} by={}", id, request.getStatus(), auth.getName());
        return adminHelpRequestService.setStatus(id, request.getStatus(), auth.getName());
    }

    /** POST /api/admin/help-requests/{id}/done — "Done helping" */
    @PostMapping("/{id}/done")
    public AdminHelpRequestResponse markDone(@PathVariable Long id, Authentication auth) {
        logger.info("[POST /api/admin/help-requests/{}/done] by={}", id, auth.getName());
        return adminHelpRequestService.markDone(id, auth.getName());
    }
}
