package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.CreateSessionRequest;
import com.ureclive.urec_live_backend.dto.PersonalRecordResponse;
import com.ureclive.urec_live_backend.dto.SessionResponse;
import com.ureclive.urec_live_backend.dto.SessionStatsResponse;
import com.ureclive.urec_live_backend.dto.WeightProgressionResponse;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.entity.WorkoutSession;
import com.ureclive.urec_live_backend.entity.WorkoutSet;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.ExerciseRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.repository.WorkoutSessionRepository;
import com.ureclive.urec_live_backend.repository.WorkoutSetRepository;
import com.ureclive.urec_live_backend.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkoutSessionService {

    private final WorkoutSessionRepository sessionRepository;
    private final WorkoutSetRepository workoutSetRepository;
    private final UserRepository userRepository;
    private final EquipmentRepository equipmentRepository;
    private final ExerciseRepository exerciseRepository;
    private final ActivityLogService activityLogService;
    private BadgeService badgeService;

    @Autowired
    public WorkoutSessionService(WorkoutSessionRepository sessionRepository,
                                 WorkoutSetRepository workoutSetRepository,
                                 UserRepository userRepository,
                                 EquipmentRepository equipmentRepository,
                                 ExerciseRepository exerciseRepository,
                                 ActivityLogService activityLogService) {
        this.sessionRepository = sessionRepository;
        this.workoutSetRepository = workoutSetRepository;
        this.userRepository = userRepository;
        this.equipmentRepository = equipmentRepository;
        this.exerciseRepository = exerciseRepository;
        this.activityLogService = activityLogService;
    }

    @Autowired
    @Lazy
    public void setBadgeService(BadgeService badgeService) {
        this.badgeService = badgeService;
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

        // Save per-set details if provided
        if (request.getSetDetails() != null && !request.getSetDetails().isEmpty()) {
            List<WorkoutSet> sets = new ArrayList<>();
            for (int i = 0; i < request.getSetDetails().size(); i++) {
                CreateSessionRequest.SetDetailDto dto = request.getSetDetails().get(i);
                WorkoutSet set = new WorkoutSet();
                set.setSession(saved);
                set.setSetNumber(i + 1);
                set.setReps(dto.getReps());
                set.setWeightLbs(dto.getWeightLbs());
                sets.add(set);
            }
            workoutSetRepository.saveAll(sets);
            saved.getSets().addAll(sets);
        }

        String machineName = machine != null ? machine.getName() : null;
        String exerciseName = exercise != null ? exercise.getName() : request.getExerciseName();
        activityLogService.log("SESSION_SAVED", username,
                "Completed " + (exerciseName != null ? exerciseName : "workout") +
                (machineName != null ? " on " + machineName : ""),
                machineName);

        badgeService.checkAndAwardBadges(username);

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

        // Last 8 ISO weeks — sessions per week
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

        // Streaks
        List<LocalDate> workoutDates = sessionRepository.findDistinctWorkoutDatesByUser(user);
        int[] streaks = calculateStreaks(workoutDates);

        // Total volume
        double totalVolume = sessionRepository.sumVolumeByUser(user);

        // Volume per week (last 8 weeks)
        Map<String, Double> volumePerWeek = recentSessions.stream()
                .collect(Collectors.groupingBy(
                        s -> weekFmt.format(s.getStartedAt()),
                        LinkedHashMap::new,
                        Collectors.summingDouble(s -> s.getSets().stream()
                                .filter(set -> set.getReps() != null && set.getWeightLbs() != null)
                                .mapToDouble(set -> set.getReps() * set.getWeightLbs())
                                .sum())
                ));

        // Muscle group breakdown
        List<Object[]> muscleRaw = sessionRepository.countSessionsByMuscleGroup(user);
        Map<String, Long> muscleGroupBreakdown = muscleRaw.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1],
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        SessionStatsResponse stats = new SessionStatsResponse();
        stats.setTotalSessions(totalSessions);
        stats.setTotalDurationSeconds(totalDuration);
        stats.setTopExercises(topExercises);
        stats.setSessionsPerWeek(sessionsPerWeek);
        stats.setCurrentStreak(streaks[0]);
        stats.setLongestStreak(streaks[1]);
        stats.setTotalVolumeLbs(totalVolume);
        stats.setVolumePerWeek(volumePerWeek);
        stats.setMuscleGroupBreakdown(muscleGroupBreakdown);
        return stats;
    }

    /**
     * Calculate current and longest workout streaks from a descending list of distinct workout dates.
     * A streak is not reset until 3+ consecutive days are missed (grace period of 2 skipped days).
     * Missing 1 or 2 days keeps the streak intact; missing 3+ days resets it.
     */
    int[] calculateStreaks(List<LocalDate> sortedDatesDesc) {
        if (sortedDatesDesc.isEmpty()) {
            return new int[]{0, 0};
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int currentStreak = 0;
        int longestStreak = 0;
        int runningStreak = 1;
        boolean countingCurrent = true;

        // Current streak is alive if most recent workout was within 2 days ago
        LocalDate mostRecent = sortedDatesDesc.get(0);
        if (ChronoUnit.DAYS.between(mostRecent, today) <= 2) {
            currentStreak = 1;
        } else {
            countingCurrent = false;
        }

        for (int i = 1; i < sortedDatesDesc.size(); i++) {
            LocalDate prev = sortedDatesDesc.get(i - 1); // more recent
            LocalDate curr = sortedDatesDesc.get(i);      // older
            // gap <= 3 means at most 2 days were skipped between workouts
            long gap = ChronoUnit.DAYS.between(curr, prev);
            if (gap <= 3) {
                runningStreak++;
                if (countingCurrent) {
                    currentStreak = runningStreak;
                }
            } else {
                longestStreak = Math.max(longestStreak, runningStreak);
                runningStreak = 1;
                countingCurrent = false;
            }
        }
        longestStreak = Math.max(longestStreak, runningStreak);

        return new int[]{currentStreak, longestStreak};
    }

    public int[] getStreaksForUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        List<LocalDate> dates = sessionRepository.findDistinctWorkoutDatesByUser(user);
        return calculateStreaks(dates);
    }

    public List<LocalDate> getWorkoutCalendar(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        Instant since = Instant.now().minus(365, ChronoUnit.DAYS);
        return sessionRepository.findDistinctWorkoutDatesByUserSince(user, since);
    }

    public List<String> getRecentMuscleGroups(String username, int days) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return sessionRepository.findDistinctMuscleGroupsByUserSince(user, since);
    }

    public List<PersonalRecordResponse> getPersonalRecords(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        List<Object[]> raw = sessionRepository.findPersonalRecordsByUser(user);
        return raw.stream()
                .map(row -> new PersonalRecordResponse((String) row[0], (Double) row[1]))
                .collect(Collectors.toList());
    }

    public List<WeightProgressionResponse> getExerciseProgression(String username, String exerciseName) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        List<Object[]> raw = sessionRepository.findWeightProgressionByExercise(user, exerciseName);
        return raw.stream()
                .map(row -> new WeightProgressionResponse((LocalDate) row[0], (Double) row[1]))
                .collect(Collectors.toList());
    }
}
