package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.CreateSessionRequest;
import com.ureclive.urec_live_backend.dto.PersonalRecordResponse;
import com.ureclive.urec_live_backend.dto.SessionResponse;
import com.ureclive.urec_live_backend.dto.SessionStatsResponse;
import com.ureclive.urec_live_backend.dto.WeightProgressionResponse;
import com.ureclive.urec_live_backend.service.WorkoutSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "*")
public class WorkoutSessionController {

    private final WorkoutSessionService sessionService;

    @Autowired
    public WorkoutSessionController(WorkoutSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(
            @RequestBody CreateSessionRequest request,
            Authentication auth) {
        SessionResponse response = sessionService.saveSession(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Page<SessionResponse>> getMyHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Page<SessionResponse> sessions = sessionService.getUserSessions(auth.getName(), page, size);
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/me/stats")
    public ResponseEntity<SessionStatsResponse> getMyStats(Authentication auth) {
        SessionStatsResponse stats = sessionService.getUserStats(auth.getName());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/me/prs")
    public ResponseEntity<List<PersonalRecordResponse>> getMyPRs(Authentication auth) {
        List<PersonalRecordResponse> prs = sessionService.getPersonalRecords(auth.getName());
        return ResponseEntity.ok(prs);
    }

    @GetMapping("/me/prs/{exerciseName}/history")
    public ResponseEntity<List<WeightProgressionResponse>> getExerciseProgression(
            @PathVariable String exerciseName,
            Authentication auth) {
        List<WeightProgressionResponse> progression =
                sessionService.getExerciseProgression(auth.getName(), exerciseName);
        return ResponseEntity.ok(progression);
    }

    @GetMapping("/me/calendar")
    public ResponseEntity<Map<String, List<LocalDate>>> getMyCalendar(Authentication auth) {
        List<LocalDate> dates = sessionService.getWorkoutCalendar(auth.getName());
        return ResponseEntity.ok(Map.of("workoutDates", dates));
    }
}
