package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.AdminExerciseResponse;
import com.ureclive.urec_live_backend.dto.CreateExerciseRequest;
import com.ureclive.urec_live_backend.dto.LinkEquipmentRequest;
import com.ureclive.urec_live_backend.dto.UpdateExerciseRequest;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.ExerciseRepository;
import com.ureclive.urec_live_backend.repository.WorkoutSessionRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final EquipmentRepository equipmentRepository;
    private final WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    public AdminExerciseService(ExerciseRepository exerciseRepository,
                                EquipmentRepository equipmentRepository,
                                WorkoutSessionRepository workoutSessionRepository) {
        this.exerciseRepository = exerciseRepository;
        this.equipmentRepository = equipmentRepository;
        this.workoutSessionRepository = workoutSessionRepository;
    }

    /**
     * Returns all exercises.
     */
    public List<AdminExerciseResponse> getAll() {
        return exerciseRepository.findAll().stream()
                .map(AdminExerciseResponse::from)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Creates a new exercise. Name must be unique.
     */
    public AdminExerciseResponse create(CreateExerciseRequest request) {
        exerciseRepository.findByNameIgnoreCase(request.getName()).ifPresent(ex -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exercise already exists: " + request.getName());
        });
        Exercise exercise = new Exercise(request.getName(), request.getMuscleGroup(), request.getGifUrl());
        exercise = exerciseRepository.save(exercise);
        return AdminExerciseResponse.from(exercise);
    }

    /**
     * Updates mutable fields on an existing exercise.
     */
    public AdminExerciseResponse update(Long id, UpdateExerciseRequest request) {
        Exercise exercise = findById(id);

        if (request.getName() != null && !request.getName().isBlank()) {
            exercise.setName(request.getName());
        }
        if (request.getMuscleGroup() != null && !request.getMuscleGroup().isBlank()) {
            exercise.setMuscleGroup(request.getMuscleGroup());
        }
        if (request.getGifUrl() != null) {
            exercise.setGifUrl(request.getGifUrl());
        }

        exercise = exerciseRepository.save(exercise);
        return AdminExerciseResponse.from(exercise);
    }

    /**
     * Deletes an exercise. Nulls out workout session references first,
     * then removes equipment-exercise links (Equipment owns the join table),
     * then hard-deletes the exercise.
     */
    @Transactional
    public void delete(Long id) {
        Exercise exercise = findById(id);

        // Null out FK in workout_sessions before deleting
        workoutSessionRepository.clearExerciseReference(id);

        // Equipment owns the join table — remove exercise from each linked equipment's set
        List<Equipment> linkedEquipment = new ArrayList<>(exercise.getEquipment());
        for (Equipment eq : linkedEquipment) {
            eq.getExercises().remove(exercise);
            equipmentRepository.save(eq);
        }

        exerciseRepository.delete(exercise);
    }

    /**
     * Links the exercise to one or more equipment records.
     * Silently skips equipment IDs that don't exist.
     */
    @Transactional
    public AdminExerciseResponse linkEquipment(Long id, LinkEquipmentRequest request) {
        final Exercise exercise = findById(id);

        for (Long equipmentId : request.getEquipmentIds()) {
            equipmentRepository.findById(equipmentId).ifPresent(eq -> {
                if (!eq.getExercises().contains(exercise)) {
                    eq.addExercise(exercise);
                    equipmentRepository.save(eq);
                }
            });
        }

        // Reload to reflect all changes
        return AdminExerciseResponse.from(findById(id));
    }

    /**
     * Unlinks a single equipment record from the exercise.
     */
    @Transactional
    public AdminExerciseResponse unlinkEquipment(Long id, Long equipmentId) {
        Exercise exercise = findById(id);
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found with id: " + equipmentId));

        equipment.removeExercise(exercise);
        equipmentRepository.save(equipment);

        exercise = findById(id);
        return AdminExerciseResponse.from(exercise);
    }

    private Exercise findById(Long id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found with id: " + id));
    }
}
