package com.ureclive.urec_live_backend.dto;

import java.time.LocalDate;

public class LeagueInfoResponse {

    private String tier;
    private double weeklyScore;
    private Integer rankInTier;
    private int totalInTier;
    private LocalDate weekStart;
    private Double nextTierThreshold;

    public LeagueInfoResponse() {}

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public double getWeeklyScore() { return weeklyScore; }
    public void setWeeklyScore(double weeklyScore) { this.weeklyScore = weeklyScore; }
    public Integer getRankInTier() { return rankInTier; }
    public void setRankInTier(Integer rankInTier) { this.rankInTier = rankInTier; }
    public int getTotalInTier() { return totalInTier; }
    public void setTotalInTier(int totalInTier) { this.totalInTier = totalInTier; }
    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }
    public Double getNextTierThreshold() { return nextTierThreshold; }
    public void setNextTierThreshold(Double nextTierThreshold) { this.nextTierThreshold = nextTierThreshold; }
}
