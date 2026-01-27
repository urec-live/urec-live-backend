package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.AuthResponse;
import com.ureclive.urec_live_backend.dto.LoginRequest;
import com.ureclive.urec_live_backend.dto.RegisterRequest;
import com.ureclive.urec_live_backend.entity.Role;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.RoleRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    @Value("${app.password-reset.base-url:http://localhost:8081/reset-password}")
    private String passwordResetBaseUrl;

    @Value("${app.password-reset.expiry-minutes:30}")
    private long passwordResetExpiryMinutes;

    public AuthResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new RuntimeException("Username is already taken!");
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Email is already registered!");
        }

        User user = new User(
            registerRequest.getUsername(),
            registerRequest.getEmail(),
            passwordEncoder.encode(registerRequest.getPassword())
        );

        // Assign default role
        Role userRole = roleRepository.findByName("USER")
            .orElseGet(() -> roleRepository.save(new Role("USER")));
        user.addRole(userRole);

        userRepository.save(user);

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPassword())
            .authorities(() -> "ROLE_USER")
            .build();

        String accessToken = jwtUtil.generateToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        return new AuthResponse(accessToken, refreshToken, user.getUsername(), user.getEmail());
    }

    public AuthResponse login(LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
            );

            User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String accessToken = jwtUtil.generateToken(userDetails);
            String refreshToken = jwtUtil.generateRefreshToken(userDetails);

            return new AuthResponse(accessToken, refreshToken, user.getUsername(), user.getEmail());
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        String username = jwtUtil.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPassword())
            .authorities(() -> "ROLE_USER")
            .build();

        String newAccessToken = jwtUtil.generateToken(userDetails);
        String newRefreshToken = jwtUtil.generateRefreshToken(userDetails);

        return new AuthResponse(newAccessToken, newRefreshToken, user.getUsername(), user.getEmail());
    }

    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email).ifPresent(user -> {
            String rawToken = generateResetToken();
            String hashedToken = hashToken(rawToken);
            user.setPasswordResetTokenHash(hashedToken);
            user.setPasswordResetTokenExpiresAt(Instant.now().plus(Duration.ofMinutes(passwordResetExpiryMinutes)));
            userRepository.save(user);

            String resetLink = buildResetLink(rawToken);
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
        });
    }

    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Invalid or expired token");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new RuntimeException("Password cannot be empty");
        }

        String hashedToken = hashToken(token);
        User user = userRepository.findByPasswordResetTokenHash(hashedToken)
                .orElseThrow(() -> new RuntimeException("Invalid or expired token"));

        Instant expiresAt = user.getPasswordResetTokenExpiresAt();
        if (expiresAt == null || Instant.now().isAfter(expiresAt)) {
            throw new RuntimeException("Invalid or expired token");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetTokenExpiresAt(null);
        userRepository.save(user);
    }

    private String buildResetLink(String rawToken) {
        if (passwordResetBaseUrl.contains("{token}")) {
            return passwordResetBaseUrl.replace("{token}", rawToken);
        }
        String separator = passwordResetBaseUrl.contains("?") ? "&" : "?";
        return passwordResetBaseUrl + separator + "token=" + rawToken;
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Unable to hash reset token", e);
        }
    }
}
