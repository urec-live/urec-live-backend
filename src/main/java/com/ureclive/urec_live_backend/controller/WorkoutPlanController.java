package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.TodayPlanResponse;
import com.ureclive.urec_live_backend.dto.WorkoutPlanRequest;
import com.ureclive.urec_live_backend.dto.WorkoutPlanResponse;
import com.ureclive.urec_live_backend.service.WorkoutPlanService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plans")
@CrossOrigin(origins = "*")
public class WorkoutPlanController {

    private final WorkoutPlanService planService;

    @Autowired
    public WorkoutPlanController(WorkoutPlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    public ResponseEntity<WorkoutPlanResponse> createPlan(
            @Valid @RequestBody WorkoutPlanRequest request,
            Authentication auth) {
        WorkoutPlanResponse response = planService.createPlan(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<WorkoutPlanResponse> getMyPlan(Authentication auth) {
        WorkoutPlanResponse plan = planService.getActivePlan(auth.getName());
        if (plan == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(plan);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkoutPlanResponse> updatePlan(
            @PathVariable Long id,
            @Valid @RequestBody WorkoutPlanRequest request,
            Authentication auth) {
        WorkoutPlanResponse response = planService.updatePlan(id, request, auth.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlan(
            @PathVariable Long id,
            Authentication auth) {
        planService.deletePlan(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/today")
    public ResponseEntity<TodayPlanResponse> getTodayPlan(Authentication auth) {
        TodayPlanResponse today = planService.getTodayPlan(auth.getName());
        if (today == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(today);
    }
}
