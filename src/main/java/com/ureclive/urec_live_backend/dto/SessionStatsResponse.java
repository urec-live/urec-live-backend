package com.ureclive.urec_live_backend.dto;

import java.util.List;
import java.util.Map;

public class SessionStatsResponse {

    private long totalSessions;
    private long totalDurationSeconds;
    private List<ExerciseCount> topExercises;
    private Map<String, Long> sessionsPerWeek;

    public SessionStatsResponse() {}

    public long getTotalSessions() { return totalSessions; }
    public void setTotalSessions(long totalSessions) { this.totalSessions = totalSessions; }

    public long getTotalDurationSeconds() { return totalDurationSeconds; }
    public void setTotalDurationSeconds(long totalDurationSeconds) { this.totalDurationSeconds = totalDurationSeconds; }

    public List<ExerciseCount> getTopExercises() { return topExercises; }
    public void setTopExercises(List<ExerciseCount> topExercises) { this.topExercises = topExercises; }

    public Map<String, Long> getSessionsPerWeek() { return sessionsPerWeek; }
    public void setSessionsPerWeek(Map<String, Long> sessionsPerWeek) { this.sessionsPerWeek = sessionsPerWeek; }

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
