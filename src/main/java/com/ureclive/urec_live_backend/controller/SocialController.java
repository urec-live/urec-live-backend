package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.PersonalRecordResponse;
import com.ureclive.urec_live_backend.dto.PublicProfileResponse;
import com.ureclive.urec_live_backend.dto.UpdateProfileRequest;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.service.WorkoutSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/social")
@CrossOrigin(origins = "*")
public class SocialController {

    private final UserRepository userRepository;
    private final WorkoutSessionService sessionService;

    @Autowired
    public SocialController(UserRepository userRepository,
                            WorkoutSessionService sessionService) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
    }

    @GetMapping("/users/{username}")
    public ResponseEntity<PublicProfileResponse> getPublicProfile(@PathVariable String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getProfileVisibility() != User.ProfileVisibility.PUBLIC) {
            return ResponseEntity.notFound().build();
        }

        int[] streaks = sessionService.getStreaksForUser(username);

        List<String> recentMuscleGroups = sessionService.getRecentMuscleGroups(username, 30);

        List<PersonalRecordResponse> prs = sessionService.getPersonalRecords(username);
        List<PersonalRecordResponse> topPRs = prs.stream().limit(3).toList();

        PublicProfileResponse response = new PublicProfileResponse();
        response.setUsername(user.getUsername());
        response.setBio(user.getBio());
        response.setCurrentStreak(streaks[0]);
        response.setLongestStreak(streaks[1]);
        response.setRecentMuscleGroups(recentMuscleGroups);
        response.setTopPRs(topPRs);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/search")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> searchUsers(
            @RequestParam String q, Authentication auth) {
        List<User> results = userRepository.findByUsernameContainingIgnoreCaseAndProfileVisibility(
                q, User.ProfileVisibility.PUBLIC, PageRequest.of(0, 20));

        List<Map<String, Object>> entries = results.stream().map(u -> {
            int currentStreak = sessionService.getStreaksForUser(u.getUsername())[0];
            return Map.<String, Object>of("username", u.getUsername(), "currentStreak", currentStreak);
        }).toList();

        return ResponseEntity.ok(Map.of("results", entries));
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<Void> updateMyProfile(
            @RequestBody UpdateProfileRequest request, Authentication auth) {
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getProfileVisibility() != null) {
            try {
                user.setProfileVisibility(User.ProfileVisibility.valueOf(request.getProfileVisibility()));
            } catch (IllegalArgumentException ignored) {}
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}
