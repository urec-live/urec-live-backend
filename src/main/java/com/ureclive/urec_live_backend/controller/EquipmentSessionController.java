package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentSession;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.service.EquipmentSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/equipment-sessions")
public class EquipmentSessionController {

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
            @PathVariable Long equipmentId,
            @RequestParam(required = false) String metadata,
            Authentication authentication
    ) {
        User user = getUserFromAuth(authentication);
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + equipmentId));

        EquipmentSession session = equipmentSessionService.startSession(user, equipment, metadata);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/end/{equipmentId}")
    public ResponseEntity<EquipmentSession> end(
            @PathVariable Long equipmentId,
            @RequestParam(required = false) String metadata,
            Authentication authentication
    ) {
        User user = getUserFromAuth(authentication);
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + equipmentId));

        EquipmentSession session = equipmentSessionService.endSession(user, equipment, metadata);
        return ResponseEntity.ok(session);
    }

    private User getUserFromAuth(Authentication authentication) {
        // Adjust this depending on what you store in Authentication principal.
        // Common patterns:
        // - authentication.getName() returns username/email
        // - principal is custom user details object
        String usernameOrEmail = authentication.getName();

        return userRepository.findByEmail(usernameOrEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found for auth name: " + usernameOrEmail));
    }
}
