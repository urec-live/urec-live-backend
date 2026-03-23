package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "workout_sets")
public class WorkoutSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private WorkoutSession session;

    @Column(nullable = false)
    private Integer setNumber;

    private Integer reps;

    private Double weightLbs;

    public WorkoutSet() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public WorkoutSession getSession() { return session; }
    public void setSession(WorkoutSession session) { this.session = session; }

    public Integer getSetNumber() { return setNumber; }
    public void setSetNumber(Integer setNumber) { this.setNumber = setNumber; }

    public Integer getReps() { return reps; }
    public void setReps(Integer reps) { this.reps = reps; }

    public Double getWeightLbs() { return weightLbs; }
    public void setWeightLbs(Double weightLbs) { this.weightLbs = weightLbs; }
}
