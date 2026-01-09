package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Entity
@Table(name = "workout_sessions")
public class WorkoutSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String exerciseName;

    @Column(nullable = false)
    private String machineId;

    @Column(nullable = false)
    private String muscleGroup;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Column(nullable = false)
    private LocalDate workoutDate;

    public WorkoutSession() {}

    public WorkoutSession(
        User user,
        String exerciseName,
        String machineId,
        String muscleGroup,
        Instant startTime,
        Instant endTime
    ) {
        this.user = user;
        this.exerciseName = exerciseName;
        this.machineId = machineId;
        this.muscleGroup = muscleGroup;
        this.startTime = startTime;
        this.endTime = endTime;
        this.workoutDate = LocalDate.ofInstant(startTime, ZoneId.systemDefault());
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public void setExerciseName(String exerciseName) {
        this.exerciseName = exerciseName;
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public String getMuscleGroup() {
        return muscleGroup;
    }

    public void setMuscleGroup(String muscleGroup) {
        this.muscleGroup = muscleGroup;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
        this.workoutDate = LocalDate.ofInstant(startTime, ZoneId.systemDefault());
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public LocalDate getWorkoutDate() {
        return workoutDate;
    }
}
