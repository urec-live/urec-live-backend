package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.DailyWorkoutResponse;
import com.ureclive.urec_live_backend.dto.WorkoutSessionRequest;
import com.ureclive.urec_live_backend.dto.WorkoutSessionResponse;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.entity.WorkoutSession;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.repository.WorkoutSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/workouts")
@CrossOrigin(origins = "*")
public class WorkoutHistoryController {

    private final UserRepository userRepository;
    private final WorkoutSessionRepository sessionRepository;

    @Autowired
    public WorkoutHistoryController(UserRepository userRepository, WorkoutSessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    @PostMapping("/session")
    public ResponseEntity<?> addSession(@RequestBody WorkoutSessionRequest request) {
        String error = validateSession(request);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("message", error));
        }

        User user = getCurrentUser();
        Instant start = Instant.ofEpochMilli(request.getStartTime());
        Instant end = Instant.ofEpochMilli(request.getEndTime());

        WorkoutSession session = new WorkoutSession(
            user,
            request.getExerciseName().trim(),
            request.getMachineId().trim(),
            request.getMuscleGroup().trim(),
            start,
            end
        );

        WorkoutSession saved = sessionRepository.save(session);
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping("/history")
    public ResponseEntity<List<DailyWorkoutResponse>> getHistory(@RequestParam(defaultValue = "28") int days) {
        User user = getCurrentUser();
        LocalDate endDate = LocalDate.now(ZoneId.systemDefault());
        LocalDate startDate = endDate.minusDays(Math.max(days, 1) - 1L);

        List<WorkoutSession> sessions =
            sessionRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateDesc(user, startDate, endDate);

        Map<String, List<WorkoutSessionResponse>> grouped = new LinkedHashMap<>();
        Map<String, Set<String>> muscleGroups = new HashMap<>();

        for (WorkoutSession session : sessions) {
            String dateKey = session.getWorkoutDate().toString();
            grouped.computeIfAbsent(dateKey, key -> new ArrayList<>()).add(toResponse(session));
            muscleGroups.computeIfAbsent(dateKey, key -> new HashSet<>()).add(session.getMuscleGroup());
        }

        List<DailyWorkoutResponse> response = new ArrayList<>();
        for (Map.Entry<String, List<WorkoutSessionResponse>> entry : grouped.entrySet()) {
            List<String> groups = new ArrayList<>(muscleGroups.getOrDefault(entry.getKey(), Set.of()));
            response.add(new DailyWorkoutResponse(entry.getKey(), entry.getValue(), groups));
        }

        return ResponseEntity.ok(response);
    }

    private WorkoutSessionResponse toResponse(WorkoutSession session) {
        return new WorkoutSessionResponse(
            session.getExerciseName(),
            session.getMachineId(),
            session.getMuscleGroup(),
            session.getStartTime().toEpochMilli(),
            session.getEndTime().toEpochMilli()
        );
    }

    private String validateSession(WorkoutSessionRequest request) {
        if (request == null) return "Missing request body";
        if (request.getExerciseName() == null || request.getExerciseName().isBlank()) {
            return "Missing exerciseName";
        }
        if (request.getMachineId() == null || request.getMachineId().isBlank()) {
            return "Missing machineId";
        }
        if (request.getMuscleGroup() == null || request.getMuscleGroup().isBlank()) {
            return "Missing muscleGroup";
        }
        if (request.getStartTime() <= 0 || request.getEndTime() <= 0) {
            return "Missing startTime/endTime";
        }
        if (request.getEndTime() < request.getStartTime()) {
            return "endTime must be after startTime";
        }
        return null;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
