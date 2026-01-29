package com.ureclive.urec_live_backend.dto;

public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private Boolean pushNotificationsEnabled;

    public UserResponse() {
    }

    public UserResponse(Long id, String username, String email, Boolean pushNotificationsEnabled) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.pushNotificationsEnabled = pushNotificationsEnabled;
    }

    public UserResponse(Long id, String username, String email) {
        this(id, username, email, true);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getPushNotificationsEnabled() {
        return pushNotificationsEnabled;
    }

    public void setPushNotificationsEnabled(Boolean pushNotificationsEnabled) {
        this.pushNotificationsEnabled = pushNotificationsEnabled;
    }
}
