package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.TodayPlanResponse;
import com.ureclive.urec_live_backend.dto.WorkoutPlanRequest;
import com.ureclive.urec_live_backend.dto.WorkoutPlanResponse;
import com.ureclive.urec_live_backend.entity.DayPlan;
import com.ureclive.urec_live_backend.entity.DayPlanItem;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.entity.WorkoutPlan;
import com.ureclive.urec_live_backend.entity.WorkoutSession;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.repository.WorkoutPlanRepository;
import com.ureclive.urec_live_backend.repository.WorkoutSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WorkoutPlanService {

    private final WorkoutPlanRepository planRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final UserRepository userRepository;

    @Autowired
    public WorkoutPlanService(WorkoutPlanRepository planRepository,
                              WorkoutSessionRepository sessionRepository,
                              UserRepository userRepository) {
        this.planRepository = planRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public WorkoutPlanResponse createPlan(WorkoutPlanRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // Deactivate any existing active plan
        planRepository.findByUserAndActiveTrue(user).ifPresent(existing -> {
            existing.setActive(false);
            planRepository.save(existing);
        });

        WorkoutPlan plan = new WorkoutPlan();
        plan.setUser(user);
        plan.setName(request.getName());
        plan.setActive(true);

        if (request.getDays() != null) {
            for (WorkoutPlanRequest.DayPlanDto dayDto : request.getDays()) {
                DayPlan dayPlan = new DayPlan();
                dayPlan.setPlan(plan);
                dayPlan.setDayOfWeek(dayDto.getDayOfWeek());
                dayPlan.setLabel(dayDto.getLabel());

                if (dayDto.getItems() != null) {
                    int sortIdx = 0;
                    for (WorkoutPlanRequest.DayPlanItemDto itemDto : dayDto.getItems()) {
                        DayPlanItem item = new DayPlanItem();
                        item.setDayPlan(dayPlan);
                        item.setMuscleGroup(itemDto.getMuscleGroup());
                        item.setTargetCount(itemDto.getTargetCount());
                        item.setSortOrder(itemDto.getSortOrder() != null ? itemDto.getSortOrder() : sortIdx);
                        dayPlan.getItems().add(item);
                        sortIdx++;
                    }
                }

                plan.getDayPlans().add(dayPlan);
            }
        }

        WorkoutPlan saved = planRepository.save(plan);
        return WorkoutPlanResponse.from(saved);
    }

    public WorkoutPlanResponse getActivePlan(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        WorkoutPlan plan = planRepository.findByUserAndActiveTrue(user)
                .orElse(null);

        return plan != null ? WorkoutPlanResponse.from(plan) : null;
    }

    @Transactional
    public WorkoutPlanResponse updatePlan(Long planId, WorkoutPlanRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        WorkoutPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));

        if (!plan.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized to update this plan");
        }

        plan.setName(request.getName());

        // Clear and flush first so orphan deletes execute before new inserts,
        // avoiding the unique constraint on (plan_id, day_of_week).
        plan.getDayPlans().clear();
        planRepository.saveAndFlush(plan);

        if (request.getDays() != null) {
            for (WorkoutPlanRequest.DayPlanDto dayDto : request.getDays()) {
                DayPlan dayPlan = new DayPlan();
                dayPlan.setPlan(plan);
                dayPlan.setDayOfWeek(dayDto.getDayOfWeek());
                dayPlan.setLabel(dayDto.getLabel());

                if (dayDto.getItems() != null) {
                    int sortIdx = 0;
                    for (WorkoutPlanRequest.DayPlanItemDto itemDto : dayDto.getItems()) {
                        DayPlanItem item = new DayPlanItem();
                        item.setDayPlan(dayPlan);
                        item.setMuscleGroup(itemDto.getMuscleGroup());
                        item.setTargetCount(itemDto.getTargetCount());
                        item.setSortOrder(itemDto.getSortOrder() != null ? itemDto.getSortOrder() : sortIdx);
                        dayPlan.getItems().add(item);
                        sortIdx++;
                    }
                }

                plan.getDayPlans().add(dayPlan);
            }
        }

        WorkoutPlan saved = planRepository.save(plan);
        return WorkoutPlanResponse.from(saved);
    }

    @Transactional
    public void deletePlan(Long planId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        WorkoutPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));

        if (!plan.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized to delete this plan");
        }

        planRepository.delete(plan);
    }

    public TodayPlanResponse getTodayPlan(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Optional<WorkoutPlan> optPlan = planRepository.findByUserAndActiveTrue(user);
        if (optPlan.isEmpty()) {
            return null;
        }

        WorkoutPlan plan = optPlan.get();
        int todayDow = LocalDate.now().getDayOfWeek().getValue(); // 1=Monday, 7=Sunday (ISO)

        // Find day plan for today
        DayPlan todayDayPlan = plan.getDayPlans().stream()
                .filter(dp -> dp.getDayOfWeek().equals(todayDow))
                .findFirst()
                .orElse(null);

        if (todayDayPlan == null) {
            // No plan for today (rest day)
            return null;
        }

        // Get today's completed sessions to calculate completion
        LocalDate today = LocalDate.now();
        Instant startOfDay = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<WorkoutSession> todaySessions = sessionRepository.findByUserAndStartedAtBetween(
                user, startOfDay, endOfDay);

        // Count completed exercises per muscle group
        Map<String, Long> completedByMuscle = todaySessions.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getMuscleGroup() != null ? s.getMuscleGroup() : "General",
                        Collectors.counting()
                ));

        // Build response
        TodayPlanResponse response = new TodayPlanResponse();
        response.setDayOfWeek(todayDow);
        response.setLabel(todayDayPlan.getLabel());
        response.setPlanName(plan.getName());
        response.setPlanActive(true);

        List<TodayPlanResponse.TodayGoalItem> items = new ArrayList<>();
        for (DayPlanItem planItem : todayDayPlan.getItems()) {
            int completed = completedByMuscle.getOrDefault(planItem.getMuscleGroup(), 0L).intValue();
            items.add(new TodayPlanResponse.TodayGoalItem(
                    planItem.getMuscleGroup(),
                    planItem.getTargetCount(),
                    completed
            ));
        }

        response.setItems(items);
        return response;
    }
}
