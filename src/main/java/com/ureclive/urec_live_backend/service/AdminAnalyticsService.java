package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.*;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.entity.WorkoutSession;
import com.ureclive.urec_live_backend.repository.ActivityLogRepository;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.repository.WorkoutSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class AdminAnalyticsService {

    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final EquipmentRepository equipmentRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ActivityLogRepository activityLogRepository;

    @Autowired
    public AdminAnalyticsService(EquipmentRepository equipmentRepository,
                                  WorkoutSessionRepository sessionRepository,
                                  UserRepository userRepository,
                                  ActivityLogRepository activityLogRepository) {
        this.equipmentRepository = equipmentRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * Live snapshot of current gym state. Reads machine statuses directly — fast.
     */
    public LiveSnapshotResponse getLiveSnapshot() {
        List<Equipment> all = equipmentRepository.findAllByDeletedFalse();
        long total = all.size();
        long occupied = all.stream().filter(e -> "In Use".equalsIgnoreCase(e.getStatus())).count();
        long available = all.stream().filter(e -> "Available".equalsIgnoreCase(e.getStatus())).count();
        long reserved = all.stream().filter(e -> "Reserved".equalsIgnoreCase(e.getStatus())).count();
        return new LiveSnapshotResponse(total, occupied, available, reserved);
    }

    /**
     * Equipment utilization over the specified period (week = 7 days, month = 30 days).
     * Most/least used machines by session count with avg duration.
     */
    public UsageStatsResponse getUsageStats(String period) {
        Instant from = periodStart(period);
        List<WorkoutSession> sessions = sessionRepository.findByStartedAtBetween(from, Instant.now());

        // Group by machine
        Map<Long, List<WorkoutSession>> byMachine = sessions.stream()
                .filter(s -> s.getMachine() != null)
                .collect(Collectors.groupingBy(s -> s.getMachine().getId()));

        List<UsageStatsResponse.MachineUsage> usageList = byMachine.entrySet().stream()
                .map(entry -> {
                    List<WorkoutSession> machineSessions = entry.getValue();
                    String machineName = machineSessions.get(0).getMachine().getName();
                    long count = machineSessions.size();
                    long avgDuration = (long) machineSessions.stream()
                            .mapToInt(s -> s.getDurationSeconds() != null ? s.getDurationSeconds() : 0)
                            .average().orElse(0);
                    return new UsageStatsResponse.MachineUsage(entry.getKey(), machineName, count, avgDuration);
                })
                .sorted(Comparator.comparingLong(UsageStatsResponse.MachineUsage::getSessionCount).reversed())
                .collect(Collectors.toList());

        List<UsageStatsResponse.MachineUsage> mostUsed = usageList.stream().limit(10).collect(Collectors.toList());
        List<UsageStatsResponse.MachineUsage> leastUsed = new ArrayList<>(usageList);
        Collections.reverse(leastUsed);
        leastUsed = leastUsed.stream().limit(10).collect(Collectors.toList());

        return new UsageStatsResponse(period, sessions.size(), mostUsed, leastUsed);
    }

    /**
     * Hourly aggregation of check-ins for the specified period. Returns all 24 hours (0–23).
     */
    public PeakHoursResponse getPeakHours(String period) {
        Instant from = periodStart(period);
        List<WorkoutSession> sessions = sessionRepository.findByStartedAtBetween(from, Instant.now());

        Map<Integer, Long> countByHour = sessions.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getStartedAt().atZone(ZoneOffset.UTC).getHour(),
                        Collectors.counting()
                ));

        List<PeakHoursResponse.HourlyCount> peakHours = IntStream.range(0, 24)
                .mapToObj(h -> new PeakHoursResponse.HourlyCount(h, countByHour.getOrDefault(h, 0L)))
                .collect(Collectors.toList());

        return new PeakHoursResponse(period, peakHours);
    }

    /**
     * Daily active users and new registrations over the specified period.
     */
    public UserAnalyticsResponse getUserAnalytics(String period) {
        Instant from = periodStart(period);
        Instant now = Instant.now();

        List<WorkoutSession> sessions = sessionRepository.findByStartedAtBetween(from, now);

        // DAU: distinct users per day
        Map<String, Long> dauByDate = sessions.stream()
                .collect(Collectors.groupingBy(
                        s -> DAY_FMT.format(s.getStartedAt()),
                        Collectors.collectingAndThen(
                                Collectors.mapping(s -> s.getUser().getId(), Collectors.toSet()),
                                set -> (long) set.size()
                        )
                ));

        long totalActiveUsers = sessions.stream()
                .map(s -> s.getUser().getId())
                .distinct()
                .count();

        // New registrations: users with createdAt in period (null-safe for legacy users)
        List<User> newUsers = userRepository.findByCreatedAtBetween(from, now);
        Map<String, Long> newRegistrationsByDate = newUsers.stream()
                .collect(Collectors.groupingBy(
                        u -> DAY_FMT.format(u.getCreatedAt()),
                        Collectors.counting()
                ));

        return new UserAnalyticsResponse(period, totalActiveUsers, newUsers.size(),
                dauByDate, newRegistrationsByDate);
    }

    /**
     * Paginated activity log, newest events first.
     */
    public Page<ActivityLogEntryResponse> getActivityLog(int page, int size) {
        return activityLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(page, size))
                .map(ActivityLogEntryResponse::from);
    }

    private Instant periodStart(String period) {
        int days = "month".equalsIgnoreCase(period) ? 30 : 7;
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}
