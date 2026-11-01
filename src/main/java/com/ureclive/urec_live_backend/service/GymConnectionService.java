package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.*;
import com.ureclive.urec_live_backend.repository.*;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@org.springframework.stereotype.Service
public class GymConnectionService {
    private final GymRepository gyms;
    private final UserRepository users;
    private final HelpRequestRepository help;
    private final GymAccessService access;

    public GymConnectionService(
            GymRepository g, UserRepository u, HelpRequestRepository h, GymAccessService a) {
        gyms = g;
        users = u;
        help = h;
        access = a;
    }

    public record GymDto(Long id, String name) {
        static GymDto from(Gym g) {
            return new GymDto(g.getId(), g.getName());
        }
    }

    public record Selection(Long gymId) {}

    public List<GymDto> directory() {
        return gyms.findByActiveTrueOrderByNameAsc().stream().map(GymDto::from).toList();
    }

    public GymDto current() {
        return gyms.findById(Optional.ofNullable(access.user().getConnectedGymId()).orElse(-1L))
                .filter(Gym::isActive)
                .map(GymDto::from)
                .orElse(null);
    }

    private void guard(User user) {
        if (help.existsByMemberAndStatusIn(user, HelpRequestStatus.OPEN))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Finish or cancel your staff-help request first");
    }

    @Transactional
    public GymDto connect(Selection selection) {
        if (selection.gymId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a gym");
        var gym =
                gyms.findById(selection.gymId())
                        .filter(Gym::isActive)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Gym unavailable"));
        var user = access.user();
        if (!Objects.equals(user.getConnectedGymId(), gym.getId())) guard(user);
        user.getPreviousGymIds().add(gym.getId());
        user.setConnectedGymId(gym.getId());
        users.save(user);
        return GymDto.from(gym);
    }

    @Transactional
    public void disconnect() {
        var user = access.user();
        guard(user);
        user.setConnectedGymId(null);
        users.save(user);
    }
}
