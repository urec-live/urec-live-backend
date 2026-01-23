package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.SessionUsageSummary;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;

    public AnalyticsController(AnalyticsService analyticsService, UserRepository userRepository) {
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
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

    @GetMapping("/usage/overall")
    public ResponseEntity<SessionUsageSummary> overallUsage(
            @RequestParam(defaultValue = "7") int days
    ) {
        Duration window = Duration.ofDays(Math.max(1, days));
        return ResponseEntity.ok(analyticsService.getOverallUsageSummary(window));
    }

    private User getUserFromAuth(Authentication authentication) {
        String usernameOrEmail = authentication.getName();
        return userRepository.findByUsername(usernameOrEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found for auth name: " + usernameOrEmail));
    }
}
