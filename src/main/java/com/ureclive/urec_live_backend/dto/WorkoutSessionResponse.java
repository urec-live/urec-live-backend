package com.ureclive.urec_live_backend.dto;

public class WorkoutSessionResponse {
    private String exerciseName;
    private String machineId;
    private String muscleGroup;
    private long startTime;
    private long endTime;

    public WorkoutSessionResponse(
        String exerciseName,
        String machineId,
        String muscleGroup,
        long startTime,
        long endTime
    ) {
        this.exerciseName = exerciseName;
        this.machineId = machineId;
        this.muscleGroup = muscleGroup;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public String getMachineId() {
        return machineId;
    }

    public String getMuscleGroup() {
        return muscleGroup;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }
}
