package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.DayPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DayPlanItemRepository extends JpaRepository<DayPlanItem, Long> {
}
