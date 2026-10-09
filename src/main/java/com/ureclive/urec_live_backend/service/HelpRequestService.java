package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.CreateHelpRequestRequest;
import com.ureclive.urec_live_backend.dto.HelpRequestResponse;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.entity.HelpRequest;
import com.ureclive.urec_live_backend.entity.HelpRequestClosedBy;
import com.ureclive.urec_live_backend.entity.HelpRequestStatus;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.ExerciseRepository;
import com.ureclive.urec_live_backend.repository.HelpRequestRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Member-facing side of "call staff" help requests. Members only ever see their own requests. */
@Service
public class HelpRequestService {

    private final HelpRequestRepository helpRequestRepository;
    private final EquipmentRepository equipmentRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final HelpDemoLinks helpDemoLinks;

    @Autowired
    public HelpRequestService(HelpRequestRepository helpRequestRepository,
                              EquipmentRepository equipmentRepository,
                              ExerciseRepository exerciseRepository,
                              UserRepository userRepository,
                              ActivityLogService activityLogService,
                              HelpDemoLinks helpDemoLinks) {
        this.helpRequestRepository = helpRequestRepository;
        this.equipmentRepository = equipmentRepository;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
        this.helpDemoLinks = helpDemoLinks;
    }

    /**
     * Calls staff to a machine. A member can have only one open request at a time, so a second
     * one (or an accidental double tap) is rejected with 409.
     */
    @Transactional
    public HelpRequestResponse create(CreateHelpRequestRequest request, String username) {
        User member = findUser(username);
        Equipment equipment = findMachine(request);

        if (helpRequestRepository.existsByMemberAndStatusIn(member, HelpRequestStatus.OPEN)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have an open help request");
        }

        Exercise exercise = resolveExercise(equipment, request.getExerciseName());
        HelpRequest saved = helpRequestRepository.save(new HelpRequest(member, equipment, exercise));

        activityLogService.log("HELP_REQUESTED", username,
                "Asked for help at " + equipment.getName(), equipment.getName());
        return toResponse(saved);
    }

    /** The member's open request, if any. */
    @Transactional(readOnly = true)
    public Optional<HelpRequestResponse> getActive(String username) {
        User member = findUser(username);
        return helpRequestRepository
                .findFirstByMemberAndStatusInOrderByCreatedAtDesc(member, HelpRequestStatus.OPEN)
                .map(this::toResponse);
    }

    /** One of the member's own requests, open or closed (polled by the app). */
    @Transactional(readOnly = true)
    public HelpRequestResponse get(Long id, String username) {
        return toResponse(findOwned(id, findUser(username)));
    }

    /** "Received help": the member says they got the help they needed. */
    @Transactional
    public HelpRequestResponse confirmReceived(Long id, String username) {
        HelpRequest request = closeOwned(id, username, HelpRequestStatus.RESOLVED);
        activityLogService.log("HELP_CLOSED", username,
                "Confirmed they received help at " + request.getEquipment().getName(),
                request.getEquipment().getName());
        return toResponse(request);
    }

    /** The member no longer needs help (pressed by accident, or the video was enough). */
    @Transactional
    public HelpRequestResponse cancel(Long id, String username) {
        HelpRequest request = closeOwned(id, username, HelpRequestStatus.CANCELLED);
        activityLogService.log("HELP_CLOSED", username,
                "Cancelled their help request at " + request.getEquipment().getName(),
                request.getEquipment().getName());
        return toResponse(request);
    }

    private HelpRequest closeOwned(Long id, String username, HelpRequestStatus finalStatus) {
        HelpRequest request = findOwned(id, findUser(username));
        if (!request.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This help request is already closed");
        }
        request.close(finalStatus, HelpRequestClosedBy.MEMBER, null, Instant.now());
        return saveOrConflict(request);
    }

    private Equipment findMachine(CreateHelpRequestRequest request) {
        Optional<Equipment> found;
        if (request.getEquipmentId() != null) {
            found = equipmentRepository.findById(request.getEquipmentId());
        } else if (request.getEquipmentCode() != null && !request.getEquipmentCode().isBlank()) {
            found = equipmentRepository.findByCode(request.getEquipmentCode().trim());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "equipmentId or equipmentCode is required");
        }
        return found.filter(e -> !e.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine not found"));
    }

    /**
     * Matches the machine's own exercises first, then any exercise by name. Anything else (such as
     * the machine code the tracker sends when the QR code had no exercise) is ignored.
     */
    Exercise resolveExercise(Equipment equipment, String exerciseName) {
        if (exerciseName == null || exerciseName.isBlank()) return null;
        String wanted = exerciseName.trim();
        return equipment.getExercises().stream()
                .filter(e -> e.getName().equalsIgnoreCase(wanted))
                .findFirst()
                .orElseGet(() -> exerciseRepository.findByNameIgnoreCase(wanted).orElse(null));
    }

    /** Someone else's request is reported as missing (404) rather than forbidden, so ids reveal nothing. */
    private HelpRequest findOwned(Long id, User member) {
        return helpRequestRepository.findWithDetailsById(id)
                .filter(r -> Objects.equals(r.getMember().getId(), member.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Help request not found"));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
    }

    /** Flushes now so a concurrent change (e.g. staff responding at the same moment) surfaces as 409. */
    private HelpRequest saveOrConflict(HelpRequest request) {
        try {
            return helpRequestRepository.saveAndFlush(request);
        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This help request was just updated; reload it");
        }
    }

    private HelpRequestResponse toResponse(HelpRequest request) {
        return HelpRequestResponse.from(request, helpDemoLinks.forRequest(request.getEquipment(), request.getExercise()));
    }
}
