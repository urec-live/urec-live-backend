package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.Equipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByCode(String code);

    List<Equipment> findAllByDeletedFalse();

    Page<Equipment> findAllByDeletedFalse(Pageable pageable);

    Page<Equipment> findAllByDeletedFalseAndNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("SELECT e FROM Equipment e JOIN e.exercises ex WHERE LOWER(ex.name) = LOWER(:exerciseName) AND e.deleted = false")
    List<Equipment> findByExerciseName(@Param("exerciseName") String exerciseName);

    List<Equipment> findAllByDeletedFalseAndFloorPlanId(Long floorPlanId);

    List<Equipment> findAllByDeletedFalseAndFloorPlanIsNull();
}
