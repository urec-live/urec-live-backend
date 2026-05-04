package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.entity.WorkoutSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    Page<WorkoutSession> findByUserOrderByStartedAtDesc(User user, Pageable pageable);

    List<WorkoutSession> findByUserAndStartedAtBetween(User user, Instant from, Instant to);

    @Query("SELECT ws.exercise.name, COUNT(ws) FROM WorkoutSession ws " +
           "WHERE ws.user = :user AND ws.exercise IS NOT NULL " +
           "GROUP BY ws.exercise.name ORDER BY COUNT(ws) DESC")
    List<Object[]> findTopExercisesByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT COUNT(ws) FROM WorkoutSession ws WHERE ws.user = :user")
    long countByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(ws.durationSeconds), 0) FROM WorkoutSession ws WHERE ws.user = :user")
    long sumDurationByUser(@Param("user") User user);

    @Query("UPDATE WorkoutSession ws SET ws.exercise = null WHERE ws.exercise.id = :exerciseId")
    void clearExerciseReference(@Param("exerciseId") Long exerciseId);

    List<WorkoutSession> findByStartedAtBetween(Instant from, Instant to);

    // Distinct dates user has worked out (for streak calculation)
    @Query("SELECT DISTINCT CAST(ws.startedAt AS LocalDate) FROM WorkoutSession ws " +
           "WHERE ws.user = :user ORDER BY CAST(ws.startedAt AS LocalDate) DESC")
    List<LocalDate> findDistinctWorkoutDatesByUser(@Param("user") User user);

    // Total volume: sum(reps * weightLbs) across all sets for a user
    @Query("SELECT COALESCE(SUM(s.reps * s.weightLbs), 0) FROM WorkoutSet s " +
           "WHERE s.session.user = :user AND s.reps IS NOT NULL AND s.weightLbs IS NOT NULL")
    double sumVolumeByUser(@Param("user") User user);

    // Session count per muscle group
    @Query("SELECT ws.muscleGroup, COUNT(ws) FROM WorkoutSession ws " +
           "WHERE ws.user = :user GROUP BY ws.muscleGroup ORDER BY COUNT(ws) DESC")
    List<Object[]> countSessionsByMuscleGroup(@Param("user") User user);

    // Personal records: max weight per exercise (across all sets)
    @Query("SELECT ws.exercise.name, MAX(s.weightLbs) FROM WorkoutSet s " +
           "JOIN s.session ws " +
           "WHERE ws.user = :user AND ws.exercise IS NOT NULL AND s.weightLbs IS NOT NULL " +
           "GROUP BY ws.exercise.name ORDER BY MAX(s.weightLbs) DESC")
    List<Object[]> findPersonalRecordsByUser(@Param("user") User user);

    // Weight progression: max weight per date for a specific exercise
    @Query("SELECT CAST(ws.startedAt AS LocalDate), MAX(s.weightLbs) FROM WorkoutSet s " +
           "JOIN s.session ws " +
           "WHERE ws.user = :user AND ws.exercise.name = :exerciseName AND s.weightLbs IS NOT NULL " +
           "GROUP BY CAST(ws.startedAt AS LocalDate) " +
           "ORDER BY CAST(ws.startedAt AS LocalDate) ASC")
    List<Object[]> findWeightProgressionByExercise(
            @Param("user") User user, @Param("exerciseName") String exerciseName);

    // Weekly volume for a single user (for league scoring)
    @Query("SELECT COALESCE(SUM(s.reps * s.weightLbs), 0) FROM WorkoutSet s " +
           "JOIN s.session ws " +
           "WHERE ws.user = :user AND ws.startedAt >= :weekStart AND ws.startedAt < :weekEnd " +
           "AND s.reps IS NOT NULL AND s.weightLbs IS NOT NULL")
    double sumVolumeByUserAndWeek(@Param("user") User user,
                                  @Param("weekStart") Instant weekStart,
                                  @Param("weekEnd") Instant weekEnd);

    // All users who have at least one session in the given week
    @Query("SELECT DISTINCT ws.user FROM WorkoutSession ws " +
           "WHERE ws.startedAt >= :weekStart AND ws.startedAt < :weekEnd")
    List<User> findActiveUsersBetween(@Param("weekStart") Instant weekStart,
                                      @Param("weekEnd") Instant weekEnd);

    // Workout dates for the past N days (for heatmap calendar)
    @Query("SELECT DISTINCT CAST(ws.startedAt AS LocalDate) FROM WorkoutSession ws " +
           "WHERE ws.user = :user AND ws.startedAt >= :since " +
           "ORDER BY CAST(ws.startedAt AS LocalDate) DESC")
    List<LocalDate> findDistinctWorkoutDatesByUserSince(@Param("user") User user,
                                                        @Param("since") Instant since);

    // Recent muscle groups used by a user since a given time
    @Query("SELECT DISTINCT ws.muscleGroup FROM WorkoutSession ws " +
           "WHERE ws.user = :user AND ws.startedAt >= :since AND ws.muscleGroup IS NOT NULL")
    List<String> findDistinctMuscleGroupsByUserSince(@Param("user") User user,
                                                     @Param("since") Instant since);
}
