package com.ureclive.urec_live_backend.dto;

public class UpdateExerciseRequest {

    private String name;
    private String muscleGroup;
    private String gifUrl;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMuscleGroup() { return muscleGroup; }
    public void setMuscleGroup(String muscleGroup) { this.muscleGroup = muscleGroup; }

    public String getGifUrl() { return gifUrl; }
    public void setGifUrl(String gifUrl) { this.gifUrl = gifUrl; }
}
