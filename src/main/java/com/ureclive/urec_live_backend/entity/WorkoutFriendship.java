package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "workout_friendships", uniqueConstraints = @UniqueConstraint(columnNames = {"first_id", "second_id"}))
public class WorkoutFriendship {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "first_id", nullable = false)
    private Long firstId;
    @Column(name = "second_id", nullable = false)
    private Long secondId;
    @Column(nullable = false)
    private Long requesterId;
    @Column(nullable = false)
    private boolean accepted;
    private Instant firstNudgedAt;
    private Instant secondNudgedAt;
}
