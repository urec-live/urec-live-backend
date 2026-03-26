package com.ureclive.urec_live_backend.dto;

public class PersonalRecordResponse {

    private String exerciseName;
    private double maxWeightLbs;

    public PersonalRecordResponse() {}

    public PersonalRecordResponse(String exerciseName, double maxWeightLbs) {
        this.exerciseName = exerciseName;
        this.maxWeightLbs = maxWeightLbs;
    }

    public String getExerciseName() { return exerciseName; }
    public void setExerciseName(String exerciseName) { this.exerciseName = exerciseName; }

    public double getMaxWeightLbs() { return maxWeightLbs; }
    public void setMaxWeightLbs(double maxWeightLbs) { this.maxWeightLbs = maxWeightLbs; }
}
