package com.ureclive.urec_live_backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ureclive.urec_live_backend.dto.WeeklySplitRequest;
import com.ureclive.urec_live_backend.dto.WeeklySplitResponse;
import com.ureclive.urec_live_backend.entity.SplitMode;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.entity.UserWorkoutSplit;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.repository.UserWorkoutSplitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/split")
@CrossOrigin(origins = "*")
public class SplitController {

    private final UserRepository userRepository;
    private final UserWorkoutSplitRepository splitRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public SplitController(
        UserRepository userRepository,
        UserWorkoutSplitRepository splitRepository,
        ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.splitRepository = splitRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<WeeklySplitResponse> getSplit() {
        User user = getCurrentUser();
        UserWorkoutSplit split = splitRepository.findByUser(user).orElse(null);
        if (split == null) {
            return ResponseEntity.ok(new WeeklySplitResponse(
                SplitMode.AUTO.name(),
                defaultManualSplit(),
                Instant.now()
            ));
        }

        Map<String, List<String>> manualSplit = parseManualSplit(split.getManualSplitJson());
        return ResponseEntity.ok(new WeeklySplitResponse(
            split.getMode().name(),
            manualSplit,
            split.getUpdatedAt()
        ));
    }

    @PutMapping
    public ResponseEntity<?> upsertSplit(@RequestBody WeeklySplitRequest request) {
        if (request.getManualSplit() != null) {
            String error = validateManualSplit(request.getManualSplit());
            if (error != null) {
                return ResponseEntity.badRequest().body(Map.of("message", error));
            }
        }

        User user = getCurrentUser();
        UserWorkoutSplit split = splitRepository.findByUser(user).orElseGet(UserWorkoutSplit::new);

        split.setUser(user);
        SplitMode mode = parseMode(request.getMode());
        split.setMode(mode);

        Map<String, List<String>> manualSplit =
            request.getManualSplit() == null ? defaultManualSplit() : request.getManualSplit();
        split.setManualSplitJson(writeManualSplit(manualSplit));

        UserWorkoutSplit saved = splitRepository.save(split);
        return ResponseEntity.ok(new WeeklySplitResponse(
            saved.getMode().name(),
            manualSplit,
            saved.getUpdatedAt()
        ));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private SplitMode parseMode(String mode) {
        if (mode == null) return SplitMode.AUTO;
        try {
            return SplitMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SplitMode.AUTO;
        }
    }

    private Map<String, List<String>> parseManualSplit(String json) {
        try {
            if (json == null || json.isBlank()) {
                return defaultManualSplit();
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, List<String>>>() {});
        } catch (Exception ex) {
            return defaultManualSplit();
        }
    }

    private String writeManualSplit(Map<String, List<String>> manualSplit) {
        try {
            return objectMapper.writeValueAsString(manualSplit);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String validateManualSplit(Map<String, List<String>> manualSplit) {
        Set<String> expectedDays = Set.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");
        if (!manualSplit.keySet().equals(expectedDays)) {
            return "Invalid day keys";
        }

        for (Map.Entry<String, List<String>> entry : manualSplit.entrySet()) {
            if (entry.getValue() == null) {
                return "Invalid split groups";
            }
            for (String group : entry.getValue()) {
                if (!ALLOWED_GROUPS.contains(group)) {
                    return "Invalid split group";
                }
            }
        }
        return null;
    }

    private Map<String, List<String>> defaultManualSplit() {
        Map<String, List<String>> split = new HashMap<>();
        split.put("Mon", List.of());
        split.put("Tue", List.of());
        split.put("Wed", List.of());
        split.put("Thu", List.of());
        split.put("Fri", List.of());
        split.put("Sat", List.of());
        split.put("Sun", List.of());
        return split;
    }

    private static final Set<String> ALLOWED_GROUPS = Set.of(
        "Chest",
        "Shoulders",
        "Triceps",
        "Back",
        "Biceps",
        "Quads",
        "Hamstrings",
        "Calves",
        "Glutes",
        "Forearms",
        "Abs",
        "Core",
        "Upper Body",
        "Lower Body",
        "Legs",
        "Cardio",
        "Pilates"
    );
}
