package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "workout_nudge_devices")
public class WorkoutNudgeDevice {
    @Id
    private String token;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Instant activeUntil;
}
