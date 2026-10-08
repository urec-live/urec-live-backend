package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.AdminHelpRequestResponse;
import com.ureclive.urec_live_backend.entity.HelpRequest;
import com.ureclive.urec_live_backend.entity.HelpRequestClosedBy;
import com.ureclive.urec_live_backend.entity.HelpRequestStatus;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.HelpRequestRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/** Staff-facing side of help requests: the live queue, responses, "Done helping" and expiry. */
@Service
public class AdminHelpRequestService {

    static final int MAX_HISTORY = 200;

    private final HelpRequestRepository helpRequestRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    @Autowired
    public AdminHelpRequestService(HelpRequestRepository helpRequestRepository,
                                   UserRepository userRepository,
                                   ActivityLogService activityLogService) {
        this.helpRequestRepository = helpRequestRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
    }

    /** Open requests, longest-waiting first. */
    @Transactional(readOnly = true)
    public List<AdminHelpRequestResponse> getOpen() {
        return helpRequestRepository.findByStatusInOrderByCreatedAtAsc(HelpRequestStatus.OPEN).stream()
                .map(AdminHelpRequestResponse::from)
                .toList();
    }

    /** Recently closed requests, newest first; {@code limit} is clamped to 1..{@value #MAX_HISTORY}. */
    @Transactional(readOnly = true)
    public List<AdminHelpRequestResponse> getHistory(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_HISTORY));
        return helpRequestRepository
                .findByStatusInOrderByClosedAtDesc(HelpRequestStatus.CLOSED, PageRequest.of(0, size)).stream()
                .map(AdminHelpRequestResponse::from)
                .toList();
    }

    /** "On the way" or "Too busy". Any other status is rejected with 400. */
    @Transactional
    public AdminHelpRequestResponse setStatus(Long id, HelpRequestStatus status, String staffUsername) {
        if (status == null || !status.isStaffResponse()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Staff can only set ON_THE_WAY or TOO_BUSY; use /done to close a request");
        }
        User staff = findUser(staffUsername);
        HelpRequest request = findOpen(id);

        if (!request.respond(staff, status, Instant.now())) {
            return AdminHelpRequestResponse.from(request);
        }
        HelpRequest saved = saveOrConflict(request);

        String member = saved.getMember().getUsername();
        String machine = saved.getEquipment().getName();
        String description = status == HelpRequestStatus.ON_THE_WAY
                ? "On the way to help " + member + " at " + machine
                : "Too busy to help " + member + " at " + machine + " right now";
        activityLogService.log("HELP_STATUS_CHANGED", staffUsername, description, machine);
        return AdminHelpRequestResponse.from(saved);
    }

    /** "Done helping": the staff member is back and closes the request. */
    @Transactional
    public AdminHelpRequestResponse markDone(Long id, String staffUsername) {
        User staff = findUser(staffUsername);
        HelpRequest request = findOpen(id);
        request.close(HelpRequestStatus.RESOLVED, HelpRequestClosedBy.STAFF, staff, Instant.now());
        HelpRequest saved = saveOrConflict(request);

        String machine = saved.getEquipment().getName();
        activityLogService.log("HELP_CLOSED", staffUsername,
                "Done helping " + saved.getMember().getUsername() + " at " + machine, machine);
        return AdminHelpRequestResponse.from(saved);
    }

    /**
     * Closes open requests nobody has updated since {@code cutoff} (the member probably left), so
     * they neither clutter the queue nor block the member's next request. Returns how many closed.
     */
    @Transactional
    public int expireIdleBefore(Instant cutoff) {
        List<HelpRequest> idle = helpRequestRepository.findByStatusInAndUpdatedAtBefore(HelpRequestStatus.OPEN, cutoff);
        Instant now = Instant.now();
        for (HelpRequest request : idle) {
            request.close(HelpRequestStatus.EXPIRED, HelpRequestClosedBy.SYSTEM, null, now);
        }
        helpRequestRepository.saveAll(idle);
        for (HelpRequest request : idle) {
            String machine = request.getEquipment().getName();
            activityLogService.log("HELP_CLOSED", "system",
                    "Help request from " + request.getMember().getUsername() + " at " + machine + " expired",
                    machine);
        }
        return idle.size();
    }

    private HelpRequest findOpen(Long id) {
        HelpRequest request = helpRequestRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Help request not found"));
        if (!request.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This help request is already closed");
        }
        return request;
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
    }

    /** Flushes now so a concurrent change (e.g. the member closing it at the same moment) surfaces as 409. */
    private HelpRequest saveOrConflict(HelpRequest request) {
        try {
            return helpRequestRepository.saveAndFlush(request);
        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This help request was just updated; reload it");
        }
    }
}
