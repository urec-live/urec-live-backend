package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.Equipment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    Optional<Equipment> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.id = :id")
    Optional<Equipment> findByIdForUpdate(@Param("id") Long id);
    
    @Query("SELECT e FROM Equipment e JOIN e.exercises ex WHERE LOWER(ex.name) = LOWER(:exerciseName)")
    List<Equipment> findByExerciseName(@Param("exerciseName") String exerciseName);
}
