package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.Equipment;

import java.util.List;
import java.util.stream.Collectors;

public class AdminEquipmentResponse {

    private Long id;
    private String code;
    private String name;
    private String status;
    private String imageUrl;
    private boolean deleted;
    private List<String> exercises;

    public AdminEquipmentResponse() {}

    public static AdminEquipmentResponse from(Equipment equipment) {
        AdminEquipmentResponse dto = new AdminEquipmentResponse();
        dto.id = equipment.getId();
        dto.code = equipment.getCode();
        dto.name = equipment.getName();
        dto.status = equipment.getStatus();
        dto.imageUrl = equipment.getImageUrl();
        dto.deleted = equipment.isDeleted();
        dto.exercises = equipment.getExercises().stream()
                .map(ex -> ex.getName())
                .sorted()
                .collect(Collectors.toList());
        return dto;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public String getImageUrl() { return imageUrl; }
    public boolean isDeleted() { return deleted; }
    public List<String> getExercises() { return exercises; }
}
