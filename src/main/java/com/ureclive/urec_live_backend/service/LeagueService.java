package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.LeaderboardResponse;
import com.ureclive.urec_live_backend.dto.LeagueInfoResponse;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.entity.UserLeagueScore;
import com.ureclive.urec_live_backend.repository.UserLeagueScoreRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.repository.WorkoutSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class LeagueService {

    // Weekly volume thresholds (lbs) for tier assignment
    static final double SILVER_THRESHOLD   =  5_000.0;
    static final double GOLD_THRESHOLD     = 15_000.0;
    static final double PLATINUM_THRESHOLD = 30_000.0;

    // Minimum minutes before a cached score is considered stale
    private static final long CACHE_TTL_MINUTES = 15;

    private final WorkoutSessionRepository sessionRepository;
    private final UserLeagueScoreRepository leagueScoreRepository;
    private final UserRepository userRepository;
    private final WorkoutSessionService sessionService;

    @Autowired
    public LeagueService(WorkoutSessionRepository sessionRepository,
                         UserLeagueScoreRepository leagueScoreRepository,
                         UserRepository userRepository,
                         WorkoutSessionService sessionService) {
        this.sessionRepository = sessionRepository;
        this.leagueScoreRepository = leagueScoreRepository;
        this.userRepository = userRepository;
        this.sessionService = sessionService;
    }

    public LeagueInfoResponse getMyLeagueInfo(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        LocalDate weekStart = getWeekStart();
        UserLeagueScore score = resolveScore(user, weekStart);

        List<UserLeagueScore> tierScores = leagueScoreRepository
                .findByWeekStartAndTierOrderByRankInTierAsc(weekStart, score.getTier());

        LeagueInfoResponse response = new LeagueInfoResponse();
        response.setTier(score.getTier());
        response.setWeeklyScore(score.getWeeklyScore());
        response.setRankInTier(score.getRankInTier());
        response.setTotalInTier(tierScores.size());
        response.setWeekStart(weekStart);
        response.setNextTierThreshold(nextTierThreshold(score.getTier()));
        return response;
    }

    public LeaderboardResponse getLeaderboard(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        LocalDate weekStart = getWeekStart();
        UserLeagueScore myScore = resolveScore(user, weekStart);
        String tier = myScore.getTier();

        List<UserLeagueScore> tierScores = leagueScoreRepository
                .findByWeekStartAndTierOrderByRankInTierAsc(weekStart, tier);

        List<LeaderboardResponse.LeaderboardEntry> entries = new ArrayList<>();
        LeaderboardResponse.LeaderboardEntry myEntry = null;
        boolean myEntryInTop = false;

        int limit = Math.min(10, tierScores.size());
        for (int i = 0; i < limit; i++) {
            UserLeagueScore s = tierScores.get(i);
            boolean isMe = s.getUser().getId().equals(user.getId());
            LeaderboardResponse.LeaderboardEntry entry = new LeaderboardResponse.LeaderboardEntry(
                    s.getRankInTier(), s.getUser().getUsername(), s.getWeeklyScore(), isMe);
            entries.add(entry);
            if (isMe) {
                myEntry = entry;
                myEntryInTop = true;
            }
        }

        if (!myEntryInTop) {
            myEntry = new LeaderboardResponse.LeaderboardEntry(
                    myScore.getRankInTier() != null ? myScore.getRankInTier() : tierScores.size() + 1,
                    user.getUsername(), myScore.getWeeklyScore(), true);
        }

        LeaderboardResponse response = new LeaderboardResponse();
        response.setTier(tier);
        response.setWeekStart(weekStart);
        response.setEntries(entries);
        response.setMyEntry(myEntry);
        return response;
    }

    // Resolve (or compute + cache) the league score for a user this week
    private UserLeagueScore resolveScore(User user, LocalDate weekStart) {
        Optional<UserLeagueScore> cached = leagueScoreRepository.findByUserAndWeekStart(user, weekStart);
        if (cached.isPresent()) {
            UserLeagueScore cs = cached.get();
            if (cs.getComputedAt().isAfter(Instant.now().minus(CACHE_TTL_MINUTES, ChronoUnit.MINUTES))) {
                return cs;
            }
        }
        return computeAndCache(user, weekStart);
    }

    private UserLeagueScore computeAndCache(User user, LocalDate weekStart) {
        Instant weekStartInstant = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant weekEndInstant   = weekStart.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();

        double volume = sessionRepository.sumVolumeByUserAndWeek(user, weekStartInstant, weekEndInstant);
        String tier = assignTier(volume);

        // Get all active users to compute rank
        List<User> activeUsers = sessionRepository.findActiveUsersBetween(weekStartInstant, weekEndInstant);

        // Compute composite scores for all users in this tier and rank them
        List<ScoredUser> tierUsers = new ArrayList<>();
        for (User u : activeUsers) {
            double v = u.getId().equals(user.getId()) ? volume
                    : sessionRepository.sumVolumeByUserAndWeek(u, weekStartInstant, weekEndInstant);
            if (assignTier(v).equals(tier)) {
                List<java.time.LocalDate> dates = sessionRepository.findDistinctWorkoutDatesByUser(u);
                int streak = sessionService.calculateStreaks(dates)[0];
                tierUsers.add(new ScoredUser(u, v, streak));
            }
        }

        // Include this user even if they have no sessions this week
        boolean userIncluded = tierUsers.stream().anyMatch(su -> su.user.getId().equals(user.getId()));
        if (!userIncluded) {
            List<java.time.LocalDate> dates = sessionRepository.findDistinctWorkoutDatesByUser(user);
            int streak = sessionService.calculateStreaks(dates)[0];
            tierUsers.add(new ScoredUser(user, 0.0, streak));
        }

        double maxVolume = tierUsers.stream().mapToDouble(su -> su.volume).max().orElse(1.0);
        double maxStreak = tierUsers.stream().mapToDouble(su -> su.streak).max().orElse(1.0);

        tierUsers.sort((a, b) -> Double.compare(b.rankScore(maxVolume, maxStreak), a.rankScore(maxVolume, maxStreak)));

        // Upsert league scores for all users in this tier
        for (int i = 0; i < tierUsers.size(); i++) {
            ScoredUser su = tierUsers.get(i);
            LocalDate ws = weekStart;
            Optional<UserLeagueScore> existingOpt = leagueScoreRepository.findByUserAndWeekStart(su.user, ws);
            UserLeagueScore ls = existingOpt.orElse(new UserLeagueScore());
            ls.setUser(su.user);
            ls.setWeekStart(ws);
            ls.setWeeklyScore(su.volume);
            ls.setTier(tier);
            ls.setRankInTier(i + 1);
            leagueScoreRepository.save(ls);
        }

        return leagueScoreRepository.findByUserAndWeekStart(user, weekStart)
                .orElseThrow(() -> new RuntimeException("League score not found after compute"));
    }

    String assignTier(double weeklyVolume) {
        if (weeklyVolume >= PLATINUM_THRESHOLD) return "PLATINUM";
        if (weeklyVolume >= GOLD_THRESHOLD)     return "GOLD";
        if (weeklyVolume >= SILVER_THRESHOLD)   return "SILVER";
        return "BRONZE";
    }

    LocalDate getWeekStart() {
        return LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private Double nextTierThreshold(String tier) {
        return switch (tier) {
            case "BRONZE"   -> SILVER_THRESHOLD;
            case "SILVER"   -> GOLD_THRESHOLD;
            case "GOLD"     -> PLATINUM_THRESHOLD;
            default         -> null;
        };
    }

    private static class ScoredUser {
        final User user;
        final double volume;
        final int streak;

        ScoredUser(User user, double volume, int streak) {
            this.user = user;
            this.volume = volume;
            this.streak = streak;
        }

        double rankScore(double maxVolume, double maxStreak) {
            double volNorm    = maxVolume > 0 ? volume / maxVolume : 0;
            double streakNorm = maxStreak > 0 ? streak / maxStreak : 0;
            return (volNorm * 0.5) + (streakNorm * 0.5);
        }
    }
}
