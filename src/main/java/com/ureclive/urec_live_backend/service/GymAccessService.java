package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.*;
import com.ureclive.urec_live_backend.repository.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.function.Predicate;

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
        return visibleFilter().test(gymId);
    }

    /**
     * visible() for filtering a list: looks the user up once. Calling visible() per item runs a
     * user query for each one (~95 machines on the Equipment tab).
     */
    public Predicate<Long> visibleFilter() {
        if (!enabled) return gymId -> true;
        Long connectedGymId = user().getConnectedGymId();
        return gymId -> gymId != null && Objects.equals(connectedGymId, gymId);
    }

    public void require(Long gymId) {
        if (!visible(gymId))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Connect to this gym in Settings first");
    }
}
