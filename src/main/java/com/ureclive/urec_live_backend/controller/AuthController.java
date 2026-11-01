package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.AuthResponse;
import com.ureclive.urec_live_backend.dto.LoginRequest;
import com.ureclive.urec_live_backend.dto.GoogleAuthRequest;
import com.ureclive.urec_live_backend.dto.RefreshTokenRequest;
import com.ureclive.urec_live_backend.dto.RegisterRequest;
import com.ureclive.urec_live_backend.service.AuthService;
import com.ureclive.urec_live_backend.service.GoogleAuthService;
import com.ureclive.urec_live_backend.security.GoogleSignInException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private GoogleAuthService googleAuthService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        try {
            AuthResponse response = authService.register(registerRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            AuthResponse response = authService.login(loginRequest);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@Valid @RequestBody GoogleAuthRequest request) {
        try {
            return ResponseEntity.ok(googleAuthService.login(request.getIdToken()));
        } catch (GoogleSignInException exception) {
            HttpStatus status = switch (exception.getReason()) {
                case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
                case ACCOUNT_CONFLICT -> HttpStatus.CONFLICT;
                case REJECTED -> HttpStatus.UNAUTHORIZED;
            };
            return ResponseEntity.status(status).body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = authService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Auth endpoint is working!");
    }
}
