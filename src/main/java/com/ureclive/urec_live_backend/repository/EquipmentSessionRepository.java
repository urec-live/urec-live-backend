package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentSession;
import com.ureclive.urec_live_backend.entity.EquipmentSessionStatus;
import com.ureclive.urec_live_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EquipmentSessionRepository extends JpaRepository<EquipmentSession, Long> {

    Optional<EquipmentSession> findByEquipmentAndStatus(
            Equipment equipment,
            EquipmentSessionStatus status
    );

    Optional<EquipmentSession> findByUserAndEquipmentAndStatus(
            User user,
            Equipment equipment,
            EquipmentSessionStatus status
    );
}
