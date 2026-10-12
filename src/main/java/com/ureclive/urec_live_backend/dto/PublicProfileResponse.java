package com.ureclive.urec_live_backend.dto;

import java.util.List;

public class PublicProfileResponse {

    private String username;
    private String bio;
    private int currentStreak;
    private int longestStreak;
    private List<String> recentMuscleGroups;
    private List<PersonalRecordResponse> topPRs;

    public PublicProfileResponse() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }
    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }
    public List<String> getRecentMuscleGroups() { return recentMuscleGroups; }
    public void setRecentMuscleGroups(List<String> recentMuscleGroups) { this.recentMuscleGroups = recentMuscleGroups; }
    public List<PersonalRecordResponse> getTopPRs() { return topPRs; }
    public void setTopPRs(List<PersonalRecordResponse> topPRs) { this.topPRs = topPRs; }
}
