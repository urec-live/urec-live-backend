package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.EquipmentEventDto;
import com.ureclive.urec_live_backend.dto.SessionUsageSummary;
import com.ureclive.urec_live_backend.dto.EquipmentUtilizationSnapshot;
import com.ureclive.urec_live_backend.dto.EquipmentUtilizationSummary;
import com.ureclive.urec_live_backend.dto.EquipmentWaitTimeEstimate;
import com.ureclive.urec_live_backend.dto.PageResponse;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentEventType;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.service.AnalyticsService;
import com.ureclive.urec_live_backend.dto.SystemAnalyticsSummary;
import com.ureclive.urec_live_backend.dto.UserAnalyticsSummary;
import com.ureclive.urec_live_backend.dto.DataQualityAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final EquipmentRepository equipmentRepository;

    public AnalyticsController(
            AnalyticsService analyticsService,
            UserRepository userRepository,
            EquipmentRepository equipmentRepository
    ) {
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @GetMapping("/usage/me")
    public ResponseEntity<SessionUsageSummary> myUsage(
            Authentication authentication,
            @RequestParam(defaultValue = "7") int days
    ) {
        User user = getUserFromAuth(authentication);
        Duration window = Duration.ofDays(Math.max(1, days));
        return ResponseEntity.ok(analyticsService.getUserUsageSummary(user, window));
    }

    @GetMapping("/user")
    public ResponseEntity<UserAnalyticsSummary> userAnalytics(
            Authentication authentication,
            @RequestParam(defaultValue = "7") int days
    ) {
        User user = getUserFromAuth(authentication);
        Duration window = Duration.ofDays(Math.max(1, days));
        return ResponseEntity.ok(analyticsService.getUserAnalyticsSummary(user, window));
    }

    @GetMapping("/usage/overall")
    public ResponseEntity<SessionUsageSummary> overallUsage(
            @RequestParam(defaultValue = "7") int days
    ) {
        Duration window = Duration.ofDays(Math.max(1, days));
        return ResponseEntity.ok(analyticsService.getOverallUsageSummary(window));
    }

    @GetMapping("/system")
    public ResponseEntity<SystemAnalyticsSummary> systemAnalytics(
            @RequestParam(defaultValue = "7") int days
    ) {
        Duration window = Duration.ofDays(Math.max(1, days));
        return ResponseEntity.ok(analyticsService.getSystemAnalyticsSummary(window));
    }

    @GetMapping("/audit")
    public ResponseEntity<DataQualityAudit> audit(
            @RequestParam(defaultValue = "7") int days
    ) {
        Duration window = Duration.ofDays(Math.max(1, days));
        return ResponseEntity.ok(analyticsService.getDataQualityAudit(window));
    }

    @GetMapping("/utilization")
    public ResponseEntity<List<EquipmentUtilizationSummary>> utilization(
            @RequestParam(defaultValue = "24") int hours
    ) {
        Duration window = Duration.ofHours(Math.max(1, hours));
        return ResponseEntity.ok(analyticsService.getUtilizationByEquipment(window, ZoneId.systemDefault()));
    }

    @GetMapping("/utilization/rolling")
    public ResponseEntity<List<EquipmentUtilizationSnapshot>> rollingUtilization(
            @RequestParam(defaultValue = "60") int minutes
    ) {
        Duration window = Duration.ofMinutes(Math.max(1, minutes));
        return ResponseEntity.ok(analyticsService.getRollingUtilization(window));
    }

    @GetMapping("/wait-time/{code}")
    public ResponseEntity<EquipmentWaitTimeEstimate> waitTime(
            @PathVariable String code,
            @RequestParam(defaultValue = "7") int days
    ) {
        Equipment equipment = equipmentRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found with code: " + code));
        Duration historyWindow = Duration.ofDays(Math.max(1, days));
        return ResponseEntity.ok(analyticsService.getWaitTimeEstimate(equipment, historyWindow));
    }

    @GetMapping("/events")
    public ResponseEntity<PageResponse<EquipmentEventDto>> events(
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) EquipmentEventType eventType,
            @RequestParam(required = false) Instant since,
            @RequestParam(required = false) Instant until,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        int safeSize = Math.min(200, Math.max(1, size));
        Pageable pageable = PageRequest.of(Math.max(0, page), safeSize, Sort.by("occurredAt").descending());
        Page<EquipmentEventDto> result = analyticsService.getEvents(
                equipmentId,
                userId,
                sessionId,
                eventType,
                since,
                until,
                pageable
        );
        PageResponse<EquipmentEventDto> response = new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
        return ResponseEntity.ok(response);
    }

    private User getUserFromAuth(Authentication authentication) {
        String usernameOrEmail = authentication.getName();
        return userRepository.findByUsername(usernameOrEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found for auth name: " + usernameOrEmail));
    }
}
