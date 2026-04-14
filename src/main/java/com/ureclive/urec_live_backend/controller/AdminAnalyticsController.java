package com.ureclive.urec_live_backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ureclive.urec_live_backend.dto.ActivityLogEntryResponse;
import com.ureclive.urec_live_backend.dto.ActivitySummaryResponse;
import com.ureclive.urec_live_backend.dto.LiveSnapshotResponse;
import com.ureclive.urec_live_backend.dto.PeakHoursResponse;
import com.ureclive.urec_live_backend.dto.UsageStatsResponse;
import com.ureclive.urec_live_backend.dto.UserAnalyticsResponse;
import com.ureclive.urec_live_backend.service.AdminAnalyticsService;

@RestController
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AdminAnalyticsController.class);

    private final AdminAnalyticsService analyticsService;

    @Autowired
    public AdminAnalyticsController(AdminAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** GET /api/admin/analytics/live */
    @GetMapping("/api/admin/analytics/live")
    public LiveSnapshotResponse getLiveSnapshot() {
        return analyticsService.getLiveSnapshot();
    }

    /** GET /api/admin/analytics/usage?period=week|month */
    @GetMapping("/api/admin/analytics/usage")
    public UsageStatsResponse getUsageStats(
            @RequestParam(defaultValue = "week") String period) {
        return analyticsService.getUsageStats(period);
    }

    /** GET /api/admin/analytics/peak-hours?period=week|month */
    @GetMapping("/api/admin/analytics/peak-hours")
    public PeakHoursResponse getPeakHours(
            @RequestParam(defaultValue = "week") String period) {
        return analyticsService.getPeakHours(period);
    }

    /** GET /api/admin/analytics/users?period=week|month */
    @GetMapping("/api/admin/analytics/users")
    public UserAnalyticsResponse getUserAnalytics(
            @RequestParam(defaultValue = "week") String period) {
        return analyticsService.getUserAnalytics(period);
    }

    /**
     * GET /api/admin/activity-log
     * Supports optional filters: eventType, search (username/description/equipment),
     * from (ISO timestamp), to (ISO timestamp), page, size
     */
    @GetMapping("/api/admin/activity-log")
    public Page<ActivityLogEntryResponse> getActivityLog(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "25")  int size,
            @RequestParam(required = false)     String eventType,
            @RequestParam(required = false)     String search,
            @RequestParam(required = false)     String from,
            @RequestParam(required = false)     String to) {
        logger.info("[GET /api/admin/activity-log] page={} size={} eventType={} search={} from={} to={}",
                page, size, eventType, search, from, to);
        return analyticsService.getActivityLog(page, size, eventType, search, from, to);
    }

    /**
     * GET /api/admin/activity-log/summary
     * Returns today's counts for each event type — used by the stats bar.
     */
    @GetMapping("/api/admin/activity-log/summary")
    public ActivitySummaryResponse getActivitySummary() {
        logger.info("[GET /api/admin/activity-log/summary]");
        return analyticsService.getActivitySummary();
    }
}