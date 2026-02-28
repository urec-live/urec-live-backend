package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.Exercise;

import java.util.List;
import java.util.stream.Collectors;

public class AdminExerciseResponse {

    private Long id;
    private String name;
    private String muscleGroup;
    private String gifUrl;
    private List<LinkedEquipment> linkedEquipment;

    public static AdminExerciseResponse from(Exercise exercise) {
        AdminExerciseResponse dto = new AdminExerciseResponse();
        dto.id = exercise.getId();
        dto.name = exercise.getName();
        dto.muscleGroup = exercise.getMuscleGroup();
        dto.gifUrl = exercise.getGifUrl();
        dto.linkedEquipment = exercise.getEquipment().stream()
                .map(eq -> new LinkedEquipment(eq.getId(), eq.getName(), eq.getCode()))
                .sorted((a, b) -> a.name.compareTo(b.name))
                .collect(Collectors.toList());
        return dto;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getMuscleGroup() { return muscleGroup; }
    public String getGifUrl() { return gifUrl; }
    public List<LinkedEquipment> getLinkedEquipment() { return linkedEquipment; }

    public static class LinkedEquipment {
        private Long id;
        private String name;
        private String code;

        public LinkedEquipment(Long id, String name, String code) {
            this.id = id;
            this.name = name;
            this.code = code;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public String getCode() { return code; }
    }
}
