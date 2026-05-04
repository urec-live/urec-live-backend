package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.BadgeResponse;
import com.ureclive.urec_live_backend.entity.UserBadge;
import com.ureclive.urec_live_backend.service.BadgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/badges")
@CrossOrigin(origins = "*")
public class BadgeController {

    private final BadgeService badgeService;

    @Autowired
    public BadgeController(BadgeService badgeService) {
        this.badgeService = badgeService;
    }

    @GetMapping("/me")
    public ResponseEntity<BadgeResponse> getMyBadges(Authentication auth) {
        List<UserBadge> badges = badgeService.getBadgesForUser(auth.getName());
        return ResponseEntity.ok(BadgeResponse.from(badges));
    }
}
