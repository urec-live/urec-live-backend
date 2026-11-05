package com.ureclive.urec_live_backend.dto;

import jakarta.validation.constraints.NotBlank;

public class ReactionRequest {

    @NotBlank(message = "Emoji is required")
    private String emoji;

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
}
