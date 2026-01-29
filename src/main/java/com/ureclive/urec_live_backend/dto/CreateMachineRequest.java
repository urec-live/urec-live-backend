package com.ureclive.urec_live_backend.dto;

public class CreateMachineRequest {
    private String code;
    private String name;
    private String imageUrl;

    public CreateMachineRequest() {
    }

    public CreateMachineRequest(String code, String name, String imageUrl) {
        this.code = code;
        this.name = name;
        this.imageUrl = imageUrl;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
