package com.ureclive.urec_live_backend.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateExerciseRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Muscle group is required")
    private String muscleGroup;

    private String gifUrl;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMuscleGroup() { return muscleGroup; }
    public void setMuscleGroup(String muscleGroup) { this.muscleGroup = muscleGroup; }

    public String getGifUrl() { return gifUrl; }
    public void setGifUrl(String gifUrl) { this.gifUrl = gifUrl; }
}
