package com.ureclive.urec_live_backend.dto;

public class CreateSessionRequest {

    private String exerciseName;
    private String machineCode;
    private String muscleGroup;
    private long startTime;      // epoch milliseconds
    private long endTime;        // epoch milliseconds
    private Integer durationSeconds;
    private String notes;

    public CreateSessionRequest() {}

    public String getExerciseName() { return exerciseName; }
    public void setExerciseName(String exerciseName) { this.exerciseName = exerciseName; }

    public String getMachineCode() { return machineCode; }
    public void setMachineCode(String machineCode) { this.machineCode = machineCode; }

    public String getMuscleGroup() { return muscleGroup; }
    public void setMuscleGroup(String muscleGroup) { this.muscleGroup = muscleGroup; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
