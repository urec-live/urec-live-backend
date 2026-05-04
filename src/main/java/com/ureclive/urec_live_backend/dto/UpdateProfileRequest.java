package com.ureclive.urec_live_backend.dto;

public class UpdateProfileRequest {
    private String profileVisibility;
    private String bio;

    public String getProfileVisibility() { return profileVisibility; }
    public void setProfileVisibility(String profileVisibility) { this.profileVisibility = profileVisibility; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
}
