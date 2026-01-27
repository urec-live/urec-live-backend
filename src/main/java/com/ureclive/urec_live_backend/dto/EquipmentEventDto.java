package com.ureclive.urec_live_backend.dto;

import java.time.Instant;

public class EquipmentEventDto {
    private Long id;
    private String eventType;
    private Long equipmentId;
    private Long sessionId;
    private Long userId;
    private Instant occurredAt;
    private String metadata;

    public EquipmentEventDto(
            Long id,
            String eventType,
            Long equipmentId,
            Long sessionId,
            Long userId,
            Instant occurredAt,
            String metadata
    ) {
        this.id = id;
        this.eventType = eventType;
        this.equipmentId = equipmentId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.occurredAt = occurredAt;
        this.metadata = metadata;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
