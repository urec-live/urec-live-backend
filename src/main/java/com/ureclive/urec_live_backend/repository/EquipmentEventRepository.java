package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.EquipmentEvent;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EquipmentEventRepository extends JpaRepository<EquipmentEvent, Long>, JpaSpecificationExecutor<EquipmentEvent> {
    List<EquipmentEvent> findByOccurredAtAfter(Instant occurredAt);
}
