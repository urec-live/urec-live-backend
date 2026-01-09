package com.ureclive.urec_live_backend.dto;

import java.util.List;

public class DailyWorkoutResponse {
    private String date;
    private List<WorkoutSessionResponse> sessions;
    private List<String> muscleGroups;

    public DailyWorkoutResponse(String date, List<WorkoutSessionResponse> sessions, List<String> muscleGroups) {
        this.date = date;
        this.sessions = sessions;
        this.muscleGroups = muscleGroups;
    }

    public String getDate() {
        return date;
    }

    public List<WorkoutSessionResponse> getSessions() {
        return sessions;
    }

    public List<String> getMuscleGroups() {
        return muscleGroups;
    }
}
