package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.Machine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MachineRepository extends JpaRepository<Machine, Long> {
    // NEW → for filtering by exercise
    List<Machine> findByExerciseIgnoreCase(String exercise);
    
    // Find machine by code
    Optional<Machine> findByCode(String code);
}
