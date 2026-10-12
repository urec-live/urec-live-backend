package com.ureclive.urec_live_backend.dto;

import java.time.LocalDate;
import java.util.List;

public class LeaderboardResponse {

    private String tier;
    private LocalDate weekStart;
    private List<LeaderboardEntry> entries;
    private LeaderboardEntry myEntry;

    public LeaderboardResponse() {}

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }
    public List<LeaderboardEntry> getEntries() { return entries; }
    public void setEntries(List<LeaderboardEntry> entries) { this.entries = entries; }
    public LeaderboardEntry getMyEntry() { return myEntry; }
    public void setMyEntry(LeaderboardEntry myEntry) { this.myEntry = myEntry; }

    public static class LeaderboardEntry {
        private int rank;
        private String username;
        private double weeklyScore;
        private boolean isMe;

        public LeaderboardEntry(int rank, String username, double weeklyScore, boolean isMe) {
            this.rank = rank;
            this.username = username;
            this.weeklyScore = weeklyScore;
            this.isMe = isMe;
        }

        public int getRank() { return rank; }
        public String getUsername() { return username; }
        public double getWeeklyScore() { return weeklyScore; }
        public boolean isMe() { return isMe; }
    }
}
