package com.ureclive.urec_live_backend.dto;

import java.time.Instant;

public class EquipmentWaitTimeEstimate {
    private Long equipmentId;
    private String code;
    private String name;
    private boolean inUse;
    private Long estimatedWaitSeconds;
    private Long averageDurationSeconds;
    private Long activeSessionElapsedSeconds;
    private Instant activeSessionStartedAt;

    public EquipmentWaitTimeEstimate() {}

    public EquipmentWaitTimeEstimate(
            Long equipmentId,
            String code,
            String name,
            boolean inUse,
            Long estimatedWaitSeconds,
            Long averageDurationSeconds,
            Long activeSessionElapsedSeconds,
            Instant activeSessionStartedAt
    ) {
        this.equipmentId = equipmentId;
        this.code = code;
        this.name = name;
        this.inUse = inUse;
        this.estimatedWaitSeconds = estimatedWaitSeconds;
        this.averageDurationSeconds = averageDurationSeconds;
        this.activeSessionElapsedSeconds = activeSessionElapsedSeconds;
        this.activeSessionStartedAt = activeSessionStartedAt;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isInUse() {
        return inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    public Long getEstimatedWaitSeconds() {
        return estimatedWaitSeconds;
    }

    public void setEstimatedWaitSeconds(Long estimatedWaitSeconds) {
        this.estimatedWaitSeconds = estimatedWaitSeconds;
    }

    public Long getAverageDurationSeconds() {
        return averageDurationSeconds;
    }

    public void setAverageDurationSeconds(Long averageDurationSeconds) {
        this.averageDurationSeconds = averageDurationSeconds;
    }

    public Long getActiveSessionElapsedSeconds() {
        return activeSessionElapsedSeconds;
    }

    public void setActiveSessionElapsedSeconds(Long activeSessionElapsedSeconds) {
        this.activeSessionElapsedSeconds = activeSessionElapsedSeconds;
    }

    public Instant getActiveSessionStartedAt() {
        return activeSessionStartedAt;
    }

    public void setActiveSessionStartedAt(Instant activeSessionStartedAt) {
        this.activeSessionStartedAt = activeSessionStartedAt;
    }
}
