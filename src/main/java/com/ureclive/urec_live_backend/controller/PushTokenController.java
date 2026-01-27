package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.PushTokenRequest;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.service.PushTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push-tokens")
public class PushTokenController {

    private final PushTokenService pushTokenService;
    private final UserRepository userRepository;

    public PushTokenController(PushTokenService pushTokenService, UserRepository userRepository) {
        this.pushTokenService = pushTokenService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> register(@RequestBody PushTokenRequest request, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        User user = getUserFromAuth(authentication);
        pushTokenService.registerToken(user, request);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private User getUserFromAuth(Authentication authentication) {
        String usernameOrEmail = authentication.getName();
        return userRepository.findByUsername(usernameOrEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found for auth name: " + usernameOrEmail));
    }
}
