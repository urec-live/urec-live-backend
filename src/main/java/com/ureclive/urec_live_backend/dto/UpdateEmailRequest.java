package com.ureclive.urec_live_backend.dto;

public class UpdateEmailRequest {
    private String newEmail;

    public UpdateEmailRequest() {
    }

    public UpdateEmailRequest(String newEmail) {
        this.newEmail = newEmail;
    }

    public String getNewEmail() {
        return newEmail;
    }

    public void setNewEmail(String newEmail) {
        this.newEmail = newEmail;
    }
}
