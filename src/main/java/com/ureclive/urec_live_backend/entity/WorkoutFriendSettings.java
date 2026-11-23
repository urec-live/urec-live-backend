package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "workout_friend_settings")
public class WorkoutFriendSettings {
    @Id
    private Long userId;
    @Column(nullable = false)
    private boolean sharing;
    @Column(nullable = false)
    private int weeklyTarget;
    @Column(nullable = false)
    private String timeZone;
}
