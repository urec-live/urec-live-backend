package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.UserBadge;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class BadgeResponse {

    private List<BadgeEntry> earned;
    private List<String> available;

    public BadgeResponse(List<BadgeEntry> earned, List<String> available) {
        this.earned = earned;
        this.available = available;
    }

    public List<BadgeEntry> getEarned() { return earned; }
    public List<String> getAvailable() { return available; }

    public static BadgeResponse from(List<UserBadge> badges) {
        List<BadgeEntry> earned = badges.stream()
                .map(b -> new BadgeEntry(b.getBadgeType(), b.getAwardedAt()))
                .toList();
        List<String> available = Arrays.asList("STREAK_7", "STREAK_30", "STREAK_100", "STREAK_365");
        return new BadgeResponse(earned, available);
    }

    public static class BadgeEntry {
        private String badgeType;
        private Instant awardedAt;

        public BadgeEntry(String badgeType, Instant awardedAt) {
            this.badgeType = badgeType;
            this.awardedAt = awardedAt;
        }

        public String getBadgeType() { return badgeType; }
        public Instant getAwardedAt() { return awardedAt; }
    }
}
