package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.entity.UserBadge;
import com.ureclive.urec_live_backend.repository.UserBadgeRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.repository.WorkoutSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class BadgeService {

    static final int[] STREAK_THRESHOLDS = {7, 30, 100, 365};
    static final String[] BADGE_TYPES = {"STREAK_7", "STREAK_30", "STREAK_100", "STREAK_365"};

    private final UserBadgeRepository badgeRepository;
    private final UserRepository userRepository;
    private final WorkoutSessionRepository sessionRepository;
    private WorkoutSessionService sessionService;

    @Autowired
    public BadgeService(UserBadgeRepository badgeRepository,
                        UserRepository userRepository,
                        WorkoutSessionRepository sessionRepository) {
        this.badgeRepository = badgeRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    @Autowired
    public void setSessionService(WorkoutSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public void checkAndAwardBadges(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return;

        List<LocalDate> dates = sessionRepository.findDistinctWorkoutDatesByUser(user);
        int[] streaks = sessionService.calculateStreaks(dates);
        int currentStreak = streaks[0];

        for (int i = 0; i < STREAK_THRESHOLDS.length; i++) {
            if (currentStreak >= STREAK_THRESHOLDS[i]) {
                awardBadgeIfNew(user, BADGE_TYPES[i]);
            }
        }
    }

    private void awardBadgeIfNew(User user, String badgeType) {
        if (badgeRepository.existsByUserAndBadgeType(user, badgeType)) return;
        try {
            badgeRepository.save(new UserBadge(user, badgeType));
        } catch (DataIntegrityViolationException ignored) {
            // concurrent award — already exists, safe to ignore
        }
    }

    public List<UserBadge> getBadgesForUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return badgeRepository.findByUser(user);
    }
}
