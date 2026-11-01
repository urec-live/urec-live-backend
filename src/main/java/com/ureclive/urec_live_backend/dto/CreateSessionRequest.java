package com.ureclive.urec_live_backend.dto;

import java.util.List;

public class CreateSessionRequest {

    private String ownerUsername;

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String v) {
        ownerUsername = v;
    }

    private String exerciseName;
    private Long exerciseId;

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long v) {
        exerciseId = v;
    }

    private String clientSessionId;

    public String getClientSessionId() {
        return clientSessionId;
    }

    public void setClientSessionId(String v) {
        clientSessionId = v;
    }

    private String machineCode;
    private String muscleGroup;
    private long startTime; // epoch milliseconds
    private long endTime; // epoch milliseconds
    private Integer durationSeconds;
    private String notes;
    private List<SetDetailDto> setDetails;

    public CreateSessionRequest() {}

    public static class SetDetailDto {
        private Integer reps;
        private Double weightLbs;
        private Integer durationSeconds;
        private Double distanceMeters;

        public Integer getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(Integer v) {
            durationSeconds = v;
        }

        public Double getDistanceMeters() {
            return distanceMeters;
        }

        public void setDistanceMeters(Double v) {
            distanceMeters = v;
        }

        public SetDetailDto() {}

        public Integer getReps() {
            return reps;
        }

        public void setReps(Integer reps) {
            this.reps = reps;
        }

        public Double getWeightLbs() {
            return weightLbs;
        }

        public void setWeightLbs(Double weightLbs) {
            this.weightLbs = weightLbs;
        }
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public void setExerciseName(String exerciseName) {
        this.exerciseName = exerciseName;
    }

    public String getMachineCode() {
        return machineCode;
    }

    public void setMachineCode(String machineCode) {
        this.machineCode = machineCode;
    }

    public String getMuscleGroup() {
        return muscleGroup;
    }

    public void setMuscleGroup(String muscleGroup) {
        this.muscleGroup = muscleGroup;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<SetDetailDto> getSetDetails() {
        return setDetails;
    }

    public void setSetDetails(List<SetDetailDto> setDetails) {
        this.setDetails = setDetails;
    }
}
