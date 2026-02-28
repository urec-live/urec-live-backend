package com.ureclive.urec_live_backend.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateEquipmentRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String status = "Available";

    private String imageUrl;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
