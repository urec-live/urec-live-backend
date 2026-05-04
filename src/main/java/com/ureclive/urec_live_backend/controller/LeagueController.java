package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.LeaderboardResponse;
import com.ureclive.urec_live_backend.dto.LeagueInfoResponse;
import com.ureclive.urec_live_backend.service.LeagueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/league")
@CrossOrigin(origins = "*")
public class LeagueController {

    private final LeagueService leagueService;

    @Autowired
    public LeagueController(LeagueService leagueService) {
        this.leagueService = leagueService;
    }

    @GetMapping("/me")
    public ResponseEntity<LeagueInfoResponse> getMyLeagueInfo(Authentication auth) {
        return ResponseEntity.ok(leagueService.getMyLeagueInfo(auth.getName()));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<LeaderboardResponse> getLeaderboard(Authentication auth) {
        return ResponseEntity.ok(leagueService.getLeaderboard(auth.getName()));
    }
}
