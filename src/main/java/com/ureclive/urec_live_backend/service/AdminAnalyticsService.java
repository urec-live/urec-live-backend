package com.ureclive.urec_live_backend.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.ureclive.urec_live_backend.dto.ActivityLogEntryResponse;
import com.ureclive.urec_live_backend.dto.ActivitySummaryResponse;
import com.ureclive.urec_live_backend.dto.LiveSnapshotResponse;
import com.ureclive.urec_live_backend.dto.PeakHoursResponse;
import com.ureclive.urec_live_backend.dto.UsageStatsResponse;
import com.ureclive.urec_live_backend.dto.UserAnalyticsResponse;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.entity.WorkoutSession;
import com.ureclive.urec_live_backend.repository.ActivityLogRepository;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.repository.WorkoutSessionRepository;

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

    public LiveSnapshotResponse getLiveSnapshot() {
        List<Equipment> all = equipmentRepository.findAllByDeletedFalse();
        long total = all.size();
        long occupied = all.stream().filter(e -> "In Use".equalsIgnoreCase(e.getStatus())).count();
        long available = all.stream().filter(e -> "Available".equalsIgnoreCase(e.getStatus())).count();
        long reserved = all.stream().filter(e -> "Reserved".equalsIgnoreCase(e.getStatus())).count();
        return new LiveSnapshotResponse(total, occupied, available, reserved);
    }

    public UsageStatsResponse getUsageStats(String period) {
        Instant from = periodStart(period);
        List<WorkoutSession> sessions = sessionRepository.findByStartedAtBetween(from, Instant.now());

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

    public UserAnalyticsResponse getUserAnalytics(String period) {
        Instant from = periodStart(period);
        Instant now = Instant.now();

        List<WorkoutSession> sessions = sessionRepository.findByStartedAtBetween(from, now);

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
     * Paginated + filtered activity log.
     * All filter params are optional — null means "no filter".
     */
    public Page<ActivityLogEntryResponse> getActivityLog(
            int page, int size,
            String eventType, String search,
            String fromStr, String toStr) {

        // Blank string → null so native query treats it as "no filter"
        String et   = (eventType != null && !eventType.isBlank()) ? eventType : null;
        String srch = (search    != null && !search.isBlank())    ? search    : null;
        String from = (fromStr   != null && !fromStr.isBlank())   ? fromStr   : null;
        String to   = (toStr     != null && !toStr.isBlank())     ? toStr     : null;

        return activityLogRepository
                .findFiltered(et, srch, from, to, PageRequest.of(page, size))
                .map(ActivityLogEntryResponse::from);
    }

    /**
     * Today's stats summary — used by the 4 cards at the top of the activity page.
     */
    public ActivitySummaryResponse getActivitySummary() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        long checkIns     = activityLogRepository.countByEventTypeAndTimestampAfter("CHECK_IN",     startOfDay);
        long checkOuts    = activityLogRepository.countByEventTypeAndTimestampAfter("CHECK_OUT",    startOfDay);
        long sessions     = activityLogRepository.countByEventTypeAndTimestampAfter("SESSION_SAVED", startOfDay);
        long registrations = activityLogRepository.countByEventTypeAndTimestampAfter("REGISTRATION", startOfDay);
        return new ActivitySummaryResponse(checkIns, checkOuts, sessions, registrations);
    }

    private Instant periodStart(String period) {
        int days = "month".equalsIgnoreCase(period) ? 30 : 7;
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}