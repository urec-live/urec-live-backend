package com.ureclive.urec_live_backend.dto;

import java.time.Instant;

public class EquipmentUtilizationSnapshot {
    private Long equipmentId;
    private String code;
    private String name;
    private Instant windowStart;
    private Instant windowEnd;
    private double utilizationPercent;

    public EquipmentUtilizationSnapshot(
            Long equipmentId,
            String code,
            String name,
            Instant windowStart,
            Instant windowEnd,
            double utilizationPercent
    ) {
        this.equipmentId = equipmentId;
        this.code = code;
        this.name = name;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.utilizationPercent = utilizationPercent;
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

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Instant windowEnd) {
        this.windowEnd = windowEnd;
    }

    public double getUtilizationPercent() {
        return utilizationPercent;
    }

    public void setUtilizationPercent(double utilizationPercent) {
        this.utilizationPercent = utilizationPercent;
    }
}
