package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.EquipmentSessionDto;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentSession;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.service.EquipmentSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/equipment-sessions")
public class EquipmentSessionController {

    private static final Logger logger = LoggerFactory.getLogger(EquipmentSessionController.class);

    private final EquipmentSessionService equipmentSessionService;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;

    public EquipmentSessionController(
            EquipmentSessionService equipmentSessionService,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository
    ) {
        this.equipmentSessionService = equipmentSessionService;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/start/{equipmentId}")
    public ResponseEntity<EquipmentSession> start(
            @PathVariable @NonNull Long equipmentId,
            @RequestParam(required = false) String metadata,
            Authentication authentication
    ) {
        User user = getUserFromAuth(authentication);
        logger.info("[api] start session user={} equipmentId={} metadata={}", user.getId(), equipmentId, metadata);
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + equipmentId));

        EquipmentSession session = equipmentSessionService.startSession(user, equipment, metadata);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/start/code/{code}")
    public ResponseEntity<EquipmentSession> startByCode(
            @PathVariable String code,
            @RequestParam(required = false) String metadata,
            Authentication authentication
    ) {
        User user = getUserFromAuth(authentication);
        logger.info("[api] start session user={} code={} metadata={}", user.getId(), code, metadata);
        Equipment equipment = equipmentRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found with code: " + code));

        EquipmentSession session = equipmentSessionService.startSession(user, equipment, metadata);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/end/{equipmentId}")
    public ResponseEntity<EquipmentSession> end(
            @PathVariable @NonNull Long equipmentId,
            @RequestParam(required = false) String metadata,
            Authentication authentication
    ) {
        User user = getUserFromAuth(authentication);
        logger.info("[api] end session user={} equipmentId={} metadata={}", user.getId(), equipmentId, metadata);
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + equipmentId));

        EquipmentSession session = equipmentSessionService.endSession(user, equipment, metadata);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/end/code/{code}")
    public ResponseEntity<EquipmentSession> endByCode(
            @PathVariable String code,
            @RequestParam(required = false) String metadata,
            Authentication authentication
    ) {
        User user = getUserFromAuth(authentication);
        logger.info("[api] end session user={} code={} metadata={}", user.getId(), code, metadata);
        Equipment equipment = equipmentRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found with code: " + code));

        EquipmentSession session = equipmentSessionService.endSession(user, equipment, metadata);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/my-active")
    public ResponseEntity<EquipmentSessionDto> myActive(Authentication authentication) {
        User user = getUserFromAuth(authentication);

        return equipmentSessionService.getMyActiveSession(user)
                .map(session -> ResponseEntity.ok(toDto(session)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/heartbeat/{equipmentId}")
    public ResponseEntity<EquipmentSession> heartbeat(
            @PathVariable @NonNull Long equipmentId,
            Authentication authentication
    ) {
        User user = getUserFromAuth(authentication);
        logger.info("[api] heartbeat user={} equipmentId={}", user.getId(), equipmentId);
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + equipmentId));

        EquipmentSession session = equipmentSessionService.heartbeat(user, equipment);
        return ResponseEntity.ok(session);
    }

    private EquipmentSessionDto toDto(EquipmentSession session) {
        Equipment equipment = session.getEquipment();
        EquipmentSessionDto.EquipmentSummary equipmentSummary = new EquipmentSessionDto.EquipmentSummary(
                equipment.getId(),
                equipment.getCode(),
                equipment.getName()
        );
        return new EquipmentSessionDto(
                session.getId(),
                session.getStatus().name(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getEndReason() == null ? null : session.getEndReason().name(),
                equipmentSummary
        );
    }

    private User getUserFromAuth(Authentication authentication) {
        // Adjust this depending on what you store in Authentication principal.
        // Common patterns:
        // - authentication.getName() returns username/email
        // - principal is custom user details object
        String usernameOrEmail = authentication.getName();

        return userRepository.findByUsername(usernameOrEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found for auth name: " + usernameOrEmail));
    }
}
