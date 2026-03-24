package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.FloorPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FloorPlanRepository extends JpaRepository<FloorPlan, Long> {
    Optional<FloorPlan> findByActiveTrue();
}
