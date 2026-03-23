package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.CreateSessionRequest;
import com.ureclive.urec_live_backend.dto.SessionResponse;
import com.ureclive.urec_live_backend.dto.SessionStatsResponse;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.entity.WorkoutSession;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.ExerciseRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.repository.WorkoutSessionRepository;
import com.ureclive.urec_live_backend.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkoutSessionService {

    private final WorkoutSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final EquipmentRepository equipmentRepository;
    private final ExerciseRepository exerciseRepository;
    private final ActivityLogService activityLogService;

    @Autowired
    public WorkoutSessionService(WorkoutSessionRepository sessionRepository,
                                 UserRepository userRepository,
                                 EquipmentRepository equipmentRepository,
                                 ExerciseRepository exerciseRepository,
                                 ActivityLogService activityLogService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.equipmentRepository = equipmentRepository;
        this.exerciseRepository = exerciseRepository;
        this.activityLogService = activityLogService;
    }

    public SessionResponse saveSession(CreateSessionRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Equipment machine = null;
        if (request.getMachineCode() != null && !request.getMachineCode().isBlank()) {
            machine = equipmentRepository.findByCode(request.getMachineCode()).orElse(null);
        }

        Exercise exercise = null;
        if (request.getExerciseName() != null && !request.getExerciseName().isBlank()) {
            exercise = exerciseRepository.findByNameIgnoreCase(request.getExerciseName()).orElse(null);
        }

        WorkoutSession session = new WorkoutSession();
        session.setUser(user);
        session.setMachine(machine);
        session.setExercise(exercise);
        session.setMuscleGroup(request.getMuscleGroup() != null ? request.getMuscleGroup() : "General");
        session.setStartedAt(Instant.ofEpochMilli(request.getStartTime()));
        session.setEndedAt(Instant.ofEpochMilli(request.getEndTime()));

        int duration = request.getDurationSeconds() != null
                ? request.getDurationSeconds()
                : (int) ((request.getEndTime() - request.getStartTime()) / 1000);
        session.setDurationSeconds(duration);
        session.setNotes(request.getNotes());

        WorkoutSession saved = sessionRepository.save(session);

        String machineName = machine != null ? machine.getName() : null;
        String exerciseName = exercise != null ? exercise.getName() : request.getExerciseName();
        activityLogService.log("SESSION_SAVED", username,
                "Completed " + (exerciseName != null ? exerciseName : "workout") +
                (machineName != null ? " on " + machineName : ""),
                machineName);

        return SessionResponse.from(saved);
    }

    public Page<SessionResponse> getUserSessions(String username, int page, int size) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        Pageable pageable = PageRequest.of(page, size);
        return sessionRepository.findByUserOrderByStartedAtDesc(user, pageable)
                .map(SessionResponse::from);
    }

    public SessionStatsResponse getUserStats(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        long totalSessions = sessionRepository.countByUser(user);
        long totalDuration = sessionRepository.sumDurationByUser(user);

        List<Object[]> topRaw = sessionRepository.findTopExercisesByUser(user, PageRequest.of(0, 5));
        List<SessionStatsResponse.ExerciseCount> topExercises = topRaw.stream()
                .map(row -> new SessionStatsResponse.ExerciseCount((String) row[0], (Long) row[1]))
                .collect(Collectors.toList());

        // Last 8 ISO weeks
        Instant eightWeeksAgo = Instant.now().minusSeconds(8L * 7 * 24 * 3600);
        List<WorkoutSession> recentSessions = sessionRepository.findByUserAndStartedAtBetween(
                user, eightWeeksAgo, Instant.now());

        DateTimeFormatter weekFmt = DateTimeFormatter.ofPattern("yyyy-'W'ww").withZone(ZoneOffset.UTC);
        Map<String, Long> sessionsPerWeek = recentSessions.stream()
                .collect(Collectors.groupingBy(
                        s -> weekFmt.format(s.getStartedAt()),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        SessionStatsResponse stats = new SessionStatsResponse();
        stats.setTotalSessions(totalSessions);
        stats.setTotalDurationSeconds(totalDuration);
        stats.setTopExercises(topExercises);
        stats.setSessionsPerWeek(sessionsPerWeek);
        return stats;
    }
}
