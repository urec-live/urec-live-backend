package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "activity_log")
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CHECK_IN, CHECK_OUT, REGISTRATION, SESSION_SAVED */
    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String description;

    private String equipmentName;

    @Column(nullable = false)
    private Instant timestamp;

    public ActivityLog() {}

    public ActivityLog(String eventType, String username, String description, String equipmentName) {
        this.eventType = eventType;
        this.username = username;
        this.description = description;
        this.equipmentName = equipmentName;
        this.timestamp = Instant.now();
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getUsername() { return username; }
    public String getDescription() { return description; }
    public String getEquipmentName() { return equipmentName; }
    public Instant getTimestamp() { return timestamp; }
}
