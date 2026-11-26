package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.service.WorkoutFriendsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workout-friends")
public class WorkoutFriendsController {
    private final WorkoutFriendsService friends;

    public WorkoutFriendsController(WorkoutFriendsService friends) {
        this.friends = friends;
    }

    public record Overview(boolean sharing, List<WorkoutFriendsService.Friend> friends) {}
    public record Settings(boolean sharing, @Min(1) @Max(7) int target, @NotBlank @Size(max = 100) String timeZone) {}
    public record Nudge(@NotBlank @Size(max = 500) String message) {}
    public record Device(@NotBlank @Pattern(regexp = "^(Expo|Exponent)PushToken\\[[A-Za-z0-9_-]{1,200}\\]$") String token) {}

    @GetMapping
    public Overview overview(Authentication auth) {
        return new Overview(friends.isSharing(auth.getName()), friends.list(auth.getName()));
    }

    @PutMapping("/settings")
    public void settings(Authentication auth, @Valid @RequestBody Settings settings) {
        friends.settings(auth.getName(), settings.sharing(), settings.target(), settings.timeZone());
    }

    @PatchMapping("/settings/target")
    public void target(Authentication auth, @Valid @RequestBody Settings settings) {
        friends.updateTarget(auth.getName(), settings.target(), settings.timeZone());
    }

    @PutMapping("/devices")
    public void registerDevice(Authentication auth, @Valid @RequestBody Device device) {
        friends.registerDevice(auth.getName(), device.token());
    }

    @DeleteMapping("/devices")
    public void removeDevice(Authentication auth, @Valid @RequestBody Device device) {
        friends.removeDevice(auth.getName(), device.token());
    }

    @PutMapping("/{username}/request")
    public void request(Authentication auth, @PathVariable String username) {
        friends.request(auth.getName(), username);
    }

    @PutMapping("/{username}/accept")
    public void accept(Authentication auth, @PathVariable String username) {
        friends.accept(auth.getName(), username);
    }

    @DeleteMapping("/{username}")
    public void remove(Authentication auth, @PathVariable String username) {
        friends.remove(auth.getName(), username);
    }

    @PostMapping("/{username}/nudge")
    public void nudge(Authentication auth, @PathVariable String username, @Valid @RequestBody Nudge nudge) {
        friends.nudge(auth.getName(), username, nudge.message());
    }
}
