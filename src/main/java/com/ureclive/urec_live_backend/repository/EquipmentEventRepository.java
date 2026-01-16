package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.EquipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentEventRepository extends JpaRepository<EquipmentEvent, Long> {
}
