package com.ureclive.urec_live_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "exercise")
public class Exercise {
    private String equipmentType = "OTHER";

    public String getEquipmentType() {
        return equipmentType;
    }

    public void setEquipmentType(String v) {
        equipmentType = v;
    }

    private String trainingType = "STRENGTH";

    public String getTrainingType() {
        return trainingType;
    }

    public void setTrainingType(String v) {
        trainingType = v;
    }

    private String loggingMode = "WEIGHT_REPS";

    public String getLoggingMode() {
        return loggingMode;
    }

    public void setLoggingMode(String v) {
        loggingMode = v;
    }

    @Column(length = 2000)
    private String instructions = null;

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String v) {
        instructions = v;
    }

    private Long ownerId = null;

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long v) {
        ownerId = v;
    }

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean archived = false;

    public boolean getArchived() {
        return archived;
    }

    public void setArchived(boolean v) {
        archived = v;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String muscleGroup;

    @Column(length = 500)
    private String gifUrl;

    @JsonIgnore
    @ManyToMany(mappedBy = "exercises")
    private Set<Equipment> equipment = new HashSet<>();

    public Exercise() {}

    public Exercise(String name, String muscleGroup, String gifUrl) {
        this.name = name;
        this.muscleGroup = muscleGroup;
        this.gifUrl = gifUrl;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMuscleGroup() {
        return muscleGroup;
    }

    public void setMuscleGroup(String muscleGroup) {
        this.muscleGroup = muscleGroup;
    }

    public String getGifUrl() {
        return gifUrl;
    }

    public void setGifUrl(String gifUrl) {
        this.gifUrl = gifUrl;
    }

    public Set<Equipment> getEquipment() {
        return equipment;
    }

    public void setEquipment(Set<Equipment> equipment) {
        this.equipment = equipment;
    }
}
