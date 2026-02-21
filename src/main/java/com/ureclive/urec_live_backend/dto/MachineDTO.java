package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.Equipment;

public class MachineDTO {
    private Long id;
    private String code;
    private String name;
    private String status;
    private String exercise;  // Primary exercise name for backward compatibility
    private String imageUrl;
    private Boolean heldByMe;

    public MachineDTO() {}

    public MachineDTO(Equipment equipment, String primaryExerciseName) {
        this.id = equipment.getId();
        this.code = equipment.getCode();
        this.name = equipment.getName();
        this.status = equipment.getStatus();
        this.exercise = primaryExerciseName;
        this.imageUrl = equipment.getImageUrl();
        this.heldByMe = false;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getExercise() { return exercise; }
    public void setExercise(String exercise) { this.exercise = exercise; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Boolean getHeldByMe() { return heldByMe; }
    public void setHeldByMe(Boolean heldByMe) { this.heldByMe = heldByMe; }
}
