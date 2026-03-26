package com.ureclive.urec_live_backend.dto;

import java.util.List;
import java.util.Map;

public class SessionStatsResponse {

    private long totalSessions;
    private long totalDurationSeconds;
    private List<ExerciseCount> topExercises;
    private Map<String, Long> sessionsPerWeek;
    private int currentStreak;
    private int longestStreak;
    private double totalVolumeLbs;
    private Map<String, Double> volumePerWeek;
    private Map<String, Long> muscleGroupBreakdown;

    public SessionStatsResponse() {}

    public long getTotalSessions() { return totalSessions; }
    public void setTotalSessions(long totalSessions) { this.totalSessions = totalSessions; }

    public long getTotalDurationSeconds() { return totalDurationSeconds; }
    public void setTotalDurationSeconds(long totalDurationSeconds) { this.totalDurationSeconds = totalDurationSeconds; }

    public List<ExerciseCount> getTopExercises() { return topExercises; }
    public void setTopExercises(List<ExerciseCount> topExercises) { this.topExercises = topExercises; }

    public Map<String, Long> getSessionsPerWeek() { return sessionsPerWeek; }
    public void setSessionsPerWeek(Map<String, Long> sessionsPerWeek) { this.sessionsPerWeek = sessionsPerWeek; }

    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }

    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }

    public double getTotalVolumeLbs() { return totalVolumeLbs; }
    public void setTotalVolumeLbs(double totalVolumeLbs) { this.totalVolumeLbs = totalVolumeLbs; }

    public Map<String, Double> getVolumePerWeek() { return volumePerWeek; }
    public void setVolumePerWeek(Map<String, Double> volumePerWeek) { this.volumePerWeek = volumePerWeek; }

    public Map<String, Long> getMuscleGroupBreakdown() { return muscleGroupBreakdown; }
    public void setMuscleGroupBreakdown(Map<String, Long> muscleGroupBreakdown) { this.muscleGroupBreakdown = muscleGroupBreakdown; }

    public static class ExerciseCount {
        private String name;
        private long count;

        public ExerciseCount(String name, long count) {
            this.name = name;
            this.count = count;
        }

        public String getName() { return name; }
        public long getCount() { return count; }
    }
}
