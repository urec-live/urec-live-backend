package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.DayPlan;
import com.ureclive.urec_live_backend.entity.WorkoutPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DayPlanRepository extends JpaRepository<DayPlan, Long> {

    Optional<DayPlan> findByPlanAndDayOfWeek(WorkoutPlan plan, Integer dayOfWeek);
}
