package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.*;
import com.ureclive.urec_live_backend.repository.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
public class GymAccessService {
    private final UserRepository users;

    @Value("${app.gym-scope.enabled:true}")
    private boolean enabled;

    public GymAccessService(UserRepository users) {
        this.users = users;
    }

    public User user() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return users.findByUsername(a.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public boolean visible(Long gymId) {
        return !enabled || (gymId != null && Objects.equals(user().getConnectedGymId(), gymId));
    }

    public void require(Long gymId) {
        if (!visible(gymId))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Connect to this gym in Settings first");
    }
}
