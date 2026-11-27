package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.ExerciseNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExerciseNoteRepository extends JpaRepository<ExerciseNote, Long> {
    Optional<ExerciseNote> findByUserIdAndExerciseId(Long userId, Long exerciseId);
}
