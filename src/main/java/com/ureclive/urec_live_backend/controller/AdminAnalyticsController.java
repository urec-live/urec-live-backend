package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.*;
import com.ureclive.urec_live_backend.service.AdminAnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
        logger.info("[GET /api/admin/analytics/live]");
        return analyticsService.getLiveSnapshot();
    }

    /** GET /api/admin/analytics/usage?period=week|month */
    @GetMapping("/api/admin/analytics/usage")
    public UsageStatsResponse getUsageStats(
            @RequestParam(defaultValue = "week") String period) {
        logger.info("[GET /api/admin/analytics/usage] period={}", period);
        return analyticsService.getUsageStats(period);
    }

    /** GET /api/admin/analytics/peak-hours?period=week|month */
    @GetMapping("/api/admin/analytics/peak-hours")
    public PeakHoursResponse getPeakHours(
            @RequestParam(defaultValue = "week") String period) {
        logger.info("[GET /api/admin/analytics/peak-hours] period={}", period);
        return analyticsService.getPeakHours(period);
    }

    /** GET /api/admin/analytics/users?period=week|month */
    @GetMapping("/api/admin/analytics/users")
    public UserAnalyticsResponse getUserAnalytics(
            @RequestParam(defaultValue = "week") String period) {
        logger.info("[GET /api/admin/analytics/users] period={}", period);
        return analyticsService.getUserAnalytics(period);
    }

    /** GET /api/admin/activity-log?page=0&size=20 */
    @GetMapping("/api/admin/activity-log")
    public Page<ActivityLogEntryResponse> getActivityLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("[GET /api/admin/activity-log] page={} size={}", page, size);
        return analyticsService.getActivityLog(page, size);
    }
}
