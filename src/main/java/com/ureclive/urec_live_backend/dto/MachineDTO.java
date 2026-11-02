package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MachineDTO {
    private Long id;
    private String code;
    private String name;
    private String status;
    private String exercise;  // Primary exercise name for backward compatibility
    // Every exercise the machine supports and their muscle groups (sorted), so a machine list
    // doesn't need a follow-up request per machine to show them
    private List<String> exerciseNames = new ArrayList<>();
    private List<String> muscleGroups = new ArrayList<>();
    private String imageUrl;
    private Boolean heldByMe;
    private Double floorX;
    private Double floorY;
    private String floorLabel;
    private Long floorPlanId;
    private String availabilitySource;
    private String secondaryStatus;
    private Double secondaryConfidence;
    private String secondaryTimestamp;

    public MachineDTO() {}

    public MachineDTO(Equipment equipment, String primaryExerciseName) {
        this.id = equipment.getId();
        this.code = equipment.getCode();
        this.name = equipment.getName();
        this.status = equipment.getStatus();
        this.exercise = primaryExerciseName;
        this.exerciseNames = sortedDistinct(equipment.getExercises().stream().map(Exercise::getName).toList());
        this.muscleGroups = sortedDistinct(equipment.getExercises().stream().map(Exercise::getMuscleGroup).toList());
        this.imageUrl = equipment.getImageUrl();
        this.heldByMe = false;
        this.floorX = equipment.getFloorX();
        this.floorY = equipment.getFloorY();
        this.floorLabel = equipment.getFloorLabel();
        this.floorPlanId = equipment.getFloorPlan() != null ? equipment.getFloorPlan().getId() : null;
        this.availabilitySource = "PRIMARY";
    }

    private static List<String> sortedDistinct(List<String> values) {
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
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

    public List<String> getExerciseNames() { return exerciseNames; }
    public void setExerciseNames(List<String> exerciseNames) { this.exerciseNames = exerciseNames; }

    public List<String> getMuscleGroups() { return muscleGroups; }
    public void setMuscleGroups(List<String> muscleGroups) { this.muscleGroups = muscleGroups; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Boolean getHeldByMe() { return heldByMe; }
    public void setHeldByMe(Boolean heldByMe) { this.heldByMe = heldByMe; }

    public Double getFloorX() { return floorX; }
    public void setFloorX(Double floorX) { this.floorX = floorX; }

    public Double getFloorY() { return floorY; }
    public void setFloorY(Double floorY) { this.floorY = floorY; }

    public String getFloorLabel() { return floorLabel; }
    public void setFloorLabel(String floorLabel) { this.floorLabel = floorLabel; }

    public Long getFloorPlanId() { return floorPlanId; }
    public void setFloorPlanId(Long floorPlanId) { this.floorPlanId = floorPlanId; }

    public String getAvailabilitySource() { return availabilitySource; }
    public void setAvailabilitySource(String availabilitySource) { this.availabilitySource = availabilitySource; }

    public String getSecondaryStatus() { return secondaryStatus; }
    public void setSecondaryStatus(String secondaryStatus) { this.secondaryStatus = secondaryStatus; }

    public Double getSecondaryConfidence() { return secondaryConfidence; }
    public void setSecondaryConfidence(Double secondaryConfidence) { this.secondaryConfidence = secondaryConfidence; }

    public String getSecondaryTimestamp() { return secondaryTimestamp; }
    public void setSecondaryTimestamp(String secondaryTimestamp) { this.secondaryTimestamp = secondaryTimestamp; }
}
