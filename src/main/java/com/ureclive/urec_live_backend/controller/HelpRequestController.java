package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.CreateHelpRequestRequest;
import com.ureclive.urec_live_backend.dto.HelpRequestResponse;
import com.ureclive.urec_live_backend.service.HelpRequestService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Member endpoints for "call staff" help requests. Deliberately outside the permitAll
 * /api/machines/** path, so every endpoint requires a signed-in member.
 */
@RestController
@RequestMapping("/api/help-requests")
@CrossOrigin(origins = "*")
public class HelpRequestController {

    private final HelpRequestService helpRequestService;

    @Autowired
    public HelpRequestController(HelpRequestService helpRequestService) {
        this.helpRequestService = helpRequestService;
    }

    /** POST /api/help-requests — call staff to a machine (by equipmentId or equipmentCode) */
    @PostMapping
    public ResponseEntity<HelpRequestResponse> create(@Valid @RequestBody CreateHelpRequestRequest request,
                                                      Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(helpRequestService.create(request, auth.getName()));
    }

    /** GET /api/help-requests/me/active — the member's open request, or 204 if there is none */
    @GetMapping("/me/active")
    public ResponseEntity<HelpRequestResponse> getActive(Authentication auth) {
        return helpRequestService.getActive(auth.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** GET /api/help-requests/{id} — one of the member's own requests (polled for status changes) */
    @GetMapping("/{id}")
    public HelpRequestResponse get(@PathVariable Long id, Authentication auth) {
        return helpRequestService.get(id, auth.getName());
    }

    /** POST /api/help-requests/{id}/received — the member confirms they received help */
    @PostMapping("/{id}/received")
    public HelpRequestResponse confirmReceived(@PathVariable Long id, Authentication auth) {
        return helpRequestService.confirmReceived(id, auth.getName());
    }

    /** POST /api/help-requests/{id}/cancel — the member no longer needs help */
    @PostMapping("/{id}/cancel")
    public HelpRequestResponse cancel(@PathVariable Long id, Authentication auth) {
        return helpRequestService.cancel(id, auth.getName());
    }
}
