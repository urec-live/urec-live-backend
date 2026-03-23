package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.entity.WorkoutSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
}
