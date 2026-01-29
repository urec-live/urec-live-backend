package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.AuthResponse;
import com.ureclive.urec_live_backend.dto.ForgotPasswordRequest;
import com.ureclive.urec_live_backend.dto.LoginRequest;
import com.ureclive.urec_live_backend.dto.RefreshTokenRequest;
import com.ureclive.urec_live_backend.dto.RegisterRequest;
import com.ureclive.urec_live_backend.dto.ResetPasswordRequest;
import com.ureclive.urec_live_backend.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest registerRequest) {
        AuthResponse response = authService.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest loginRequest) {
        AuthResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Auth endpoint is working!");
    }
}
