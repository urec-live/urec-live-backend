package com.ureclive.urec_live_backend.dto;

import java.time.Instant;

public class EquipmentUtilizationPoint {
    private Instant hourStart;
    private double utilizationPercent;

    public EquipmentUtilizationPoint() {}

    public EquipmentUtilizationPoint(Instant hourStart, double utilizationPercent) {
        this.hourStart = hourStart;
        this.utilizationPercent = utilizationPercent;
    }

    public Instant getHourStart() {
        return hourStart;
    }

    public void setHourStart(Instant hourStart) {
        this.hourStart = hourStart;
    }

    public double getUtilizationPercent() {
        return utilizationPercent;
    }

    public void setUtilizationPercent(double utilizationPercent) {
        this.utilizationPercent = utilizationPercent;
    }
}
