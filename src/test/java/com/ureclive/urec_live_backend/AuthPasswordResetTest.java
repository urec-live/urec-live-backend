package com.ureclive.urec_live_backend;

import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class AuthPasswordResetTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void requestPasswordResetStoresTokenAndExpiry() {
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = userRepository.save(new User(
                "reset_user_" + suffix,
                "reset_" + suffix + "@example.com",
                passwordEncoder.encode("password")
        ));

        authService.requestPasswordReset(user.getEmail());

        Long userId = Objects.requireNonNull(user.getId());
        User reloaded = userRepository.findById(userId).orElseThrow();
        assertThat(reloaded.getPasswordResetTokenHash()).isNotBlank();
        assertThat(reloaded.getPasswordResetTokenExpiresAt()).isNotNull();
    }

    @Test
    @Transactional
    void resetPasswordWithValidTokenUpdatesPasswordAndClearsToken() {
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = userRepository.save(new User(
                "reset_valid_user_" + suffix,
                "reset_valid_" + suffix + "@example.com",
                passwordEncoder.encode("old_password")
        ));

        String rawToken = "token_" + suffix;
        user.setPasswordResetTokenHash(sha256Hex(rawToken));
        user.setPasswordResetTokenExpiresAt(Instant.now().plusSeconds(600));
        userRepository.save(user);

        authService.resetPassword(rawToken, "new_password");

        Long userId = Objects.requireNonNull(user.getId());
        User updated = userRepository.findById(userId).orElseThrow();
        assertThat(passwordEncoder.matches("new_password", updated.getPassword())).isTrue();
        assertThat(updated.getPasswordResetTokenHash()).isNull();
        assertThat(updated.getPasswordResetTokenExpiresAt()).isNull();
    }

    @Test
    @Transactional
    void resetPasswordWithExpiredTokenFails() {
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = userRepository.save(new User(
                "reset_expired_user_" + suffix,
                "reset_expired_" + suffix + "@example.com",
                passwordEncoder.encode("old_password")
        ));

        String rawToken = "expired_" + suffix;
        user.setPasswordResetTokenHash(sha256Hex(rawToken));
        user.setPasswordResetTokenExpiresAt(Instant.now().minusSeconds(60));
        userRepository.save(user);

        assertThrows(RuntimeException.class, () -> authService.resetPassword(rawToken, "new_password"));
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
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
            throw new RuntimeException(e);
        }
    }
}
