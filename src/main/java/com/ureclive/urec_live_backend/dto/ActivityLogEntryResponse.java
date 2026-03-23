package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.ActivityLog;

import java.time.Instant;

public class ActivityLogEntryResponse {
    private Long id;
    private String eventType;
    private String username;
    private String description;
    private String equipmentName;
    private Instant timestamp;

    public static ActivityLogEntryResponse from(ActivityLog log) {
        ActivityLogEntryResponse dto = new ActivityLogEntryResponse();
        dto.id = log.getId();
        dto.eventType = log.getEventType();
        dto.username = log.getUsername();
        dto.description = log.getDescription();
        dto.equipmentName = log.getEquipmentName();
        dto.timestamp = log.getTimestamp();
        return dto;
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getUsername() { return username; }
    public String getDescription() { return description; }
    public String getEquipmentName() { return equipmentName; }
    public Instant getTimestamp() { return timestamp; }
}
