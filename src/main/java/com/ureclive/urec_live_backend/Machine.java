package com.ureclive.urec_live_backend;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Machine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String status;   // Available, In Use, Reserved
    private String exercise; // NEW → allows filtering by exercise

    public Machine() {}

    public Machine(String name, String status) {
        this.name = name;
        this.status = status;
    }

    public Machine(String name, String status, String exercise) {
        this.name = name;
        this.status = status;
        this.exercise = exercise;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public String getExercise() { return exercise; }

    public void setName(String name) { this.name = name; }
    public void setStatus(String status) { this.status = status; }
    public void setExercise(String exercise) { this.exercise = exercise; }
}
