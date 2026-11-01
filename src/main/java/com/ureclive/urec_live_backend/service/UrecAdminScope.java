package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.repository.GymRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UrecAdminScope {
    private final GymRepository gyms;

    public UrecAdminScope(GymRepository gyms) {
        this.gyms = gyms;
    }

    public Long id() {
        return gyms.findByName("UREC").orElseThrow().getId();
    }

    public boolean includes(Long gymId) {
        return java.util.Objects.equals(id(), gymId);
    }

    public void require(Long gymId) {
        if (!includes(gymId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Facility record unavailable");
    }
}
