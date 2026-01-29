package com.ureclive.urec_live_backend.dto;

import java.util.Map;

public class UserStatsDto {
    private int currentStreak;
    private int totalWorkoutsThisWeek;
    private double totalHoursThisWeek;
    private Map<String, Integer> weeklySplit; // "Legs" -> 3

    public UserStatsDto(int currentStreak, int totalWorkoutsThisWeek, double totalHoursThisWeek,
            Map<String, Integer> weeklySplit) {
        this.currentStreak = currentStreak;
        this.totalWorkoutsThisWeek = totalWorkoutsThisWeek;
        this.totalHoursThisWeek = totalHoursThisWeek;
        this.weeklySplit = weeklySplit;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getTotalWorkoutsThisWeek() {
        return totalWorkoutsThisWeek;
    }

    public void setTotalWorkoutsThisWeek(int totalWorkoutsThisWeek) {
        this.totalWorkoutsThisWeek = totalWorkoutsThisWeek;
    }

    public double getTotalHoursThisWeek() {
        return totalHoursThisWeek;
    }

    public void setTotalHoursThisWeek(double totalHoursThisWeek) {
        this.totalHoursThisWeek = totalHoursThisWeek;
    }

    public Map<String, Integer> getWeeklySplit() {
        return weeklySplit;
    }

    public void setWeeklySplit(Map<String, Integer> weeklySplit) {
        this.weeklySplit = weeklySplit;
    }
}
