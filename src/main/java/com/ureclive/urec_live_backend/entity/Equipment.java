package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "equipment")
public class Equipment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String code;  // Unique code for QR scanning

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status;  // Available, In Use, Reserved

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted = false;

    @Column(name = "floor_x")
    private Double floorX;

    @Column(name = "floor_y")
    private Double floorY;

    @Column(name = "floor_label", length = 50)
    private String floorLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_plan_id")
    private FloorPlan floorPlan;

    @ManyToMany
    @JoinTable(
        name = "equipment_exercise",
        joinColumns = @JoinColumn(name = "equipment_id"),
        inverseJoinColumns = @JoinColumn(name = "exercise_id")
    )
    private Set<Exercise> exercises = new HashSet<>();

    public Equipment() {}

    public Equipment(String code, String name, String status, String imageUrl) {
        this.code = code;
        this.name = name;
        this.status = status;
        this.imageUrl = imageUrl;
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

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Set<Exercise> getExercises() { return exercises; }
    public void setExercises(Set<Exercise> exercises) { this.exercises = exercises; }

    public void addExercise(Exercise exercise) {
        this.exercises.add(exercise);
        exercise.getEquipment().add(this);
    }

    public void removeExercise(Exercise exercise) {
        this.exercises.remove(exercise);
        exercise.getEquipment().remove(this);
    }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public Double getFloorX() { return floorX; }
    public void setFloorX(Double floorX) { this.floorX = floorX; }

    public Double getFloorY() { return floorY; }
    public void setFloorY(Double floorY) { this.floorY = floorY; }

    public String getFloorLabel() { return floorLabel; }
    public void setFloorLabel(String floorLabel) { this.floorLabel = floorLabel; }

    public FloorPlan getFloorPlan() { return floorPlan; }
    public void setFloorPlan(FloorPlan floorPlan) { this.floorPlan = floorPlan; }
}
