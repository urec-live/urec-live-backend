package com.ureclive.urec_live_backend.dto;

import java.time.LocalDate;

public class WeightProgressionResponse {

    private LocalDate date;
    private double maxWeightLbs;

    public WeightProgressionResponse() {}

    public WeightProgressionResponse(LocalDate date, double maxWeightLbs) {
        this.date = date;
        this.maxWeightLbs = maxWeightLbs;
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public double getMaxWeightLbs() { return maxWeightLbs; }
    public void setMaxWeightLbs(double maxWeightLbs) { this.maxWeightLbs = maxWeightLbs; }
}
