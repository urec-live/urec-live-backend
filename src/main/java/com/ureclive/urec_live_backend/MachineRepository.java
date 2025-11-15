package com.ureclive.urec_live_backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MachineRepository extends JpaRepository<Machine, Long> {
    // NEW → for filtering by exercise
    List<Machine> findByExerciseIgnoreCase(String exercise);
}
