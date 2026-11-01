package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.Exercise;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    Optional<Exercise> findFirstByNameIgnoreCaseAndOwnerIdIsNull(String name);

    default Optional<Exercise> findByNameIgnoreCase(String name) {
        return findFirstByNameIgnoreCaseAndOwnerIdIsNull(name);
    }
}
