package com.ureclive.urec_live_backend.dto;

import java.time.Instant;

public class EquipmentStatusUpdate {
    private Long equipmentId;
    private String status;      // "AVAILABLE" or "IN_USE"
    private Long sessionId;     // nullable when available
    private Long userId;        // nullable when available
    private Instant occurredAt;

    public EquipmentStatusUpdate() {}

    public EquipmentStatusUpdate(Long equipmentId, String status, Long sessionId, Long userId, Instant occurredAt) {
        this.equipmentId = equipmentId;
        this.status = status;
        this.sessionId = sessionId;
        this.userId = userId;
        this.occurredAt = occurredAt;
    }

    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
