package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.PushTokenRequest;
import com.ureclive.urec_live_backend.entity.PushToken;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.PushTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PushTokenService {
    private final PushTokenRepository pushTokenRepository;

    public PushTokenService(PushTokenRepository pushTokenRepository) {
        this.pushTokenRepository = pushTokenRepository;
    }

    public PushToken registerToken(User user, PushTokenRequest request) {
        if (request == null || request.getToken() == null || request.getToken().isBlank()) {
            throw new IllegalArgumentException("Push token is required");
        }

        PushToken token = pushTokenRepository.findByToken(request.getToken())
                .orElseGet(PushToken::new);

        token.setToken(request.getToken().trim());
        token.setUser(user);
        token.setPlatform(request.getPlatform());
        token.setDeviceName(request.getDeviceName());
        token.setLastSeenAt(Instant.now());

        return pushTokenRepository.save(token);
    }
}
