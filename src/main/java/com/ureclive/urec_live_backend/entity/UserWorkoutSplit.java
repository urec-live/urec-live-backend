package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_workout_splits")
public class UserWorkoutSplit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SplitMode mode = SplitMode.AUTO;

    @Column(nullable = false, columnDefinition = "text")
    private String manualSplitJson = "{}";

    @Column(nullable = false)
    private Instant updatedAt;

    public UserWorkoutSplit() {}

    public UserWorkoutSplit(User user, SplitMode mode, String manualSplitJson) {
        this.user = user;
        this.mode = mode;
        this.manualSplitJson = manualSplitJson;
    }

    @PrePersist
    @PreUpdate
    public void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public SplitMode getMode() {
        return mode;
    }

    public void setMode(SplitMode mode) {
        this.mode = mode;
    }

    public String getManualSplitJson() {
        return manualSplitJson;
    }

    public void setManualSplitJson(String manualSplitJson) {
        this.manualSplitJson = manualSplitJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
