package com.ureclive.urec_live_backend.dto;

public class UpdateEquipmentRequest {

    private String name;
    private String status;
    private String imageUrl;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
