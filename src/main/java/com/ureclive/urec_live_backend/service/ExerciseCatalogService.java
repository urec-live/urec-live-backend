package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.*;
import com.ureclive.urec_live_backend.repository.*;

import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@org.springframework.stereotype.Service
public class ExerciseCatalogService {
    private final ExerciseRepository exercises;
    private final GymAccessService access;

    public ExerciseCatalogService(ExerciseRepository e, GymAccessService a) {
        exercises = e;
        access = a;
    }

    public record ExerciseDto(
            Long id,
            String name,
            String muscleGroup,
            String equipmentType,
            String trainingType,
            String loggingMode,
            String instructions,
            String gifUrl,
            boolean custom) {
        static ExerciseDto from(Exercise e) {
            return new ExerciseDto(
                    e.getId(),
                    e.getName(),
                    e.getMuscleGroup(),
                    e.getEquipmentType(),
                    e.getTrainingType(),
                    e.getLoggingMode(),
                    e.getInstructions(),
                    e.getGifUrl(),
                    e.getOwnerId() != null);
        }
    }

    public record Input(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 150)
                    String name,
            @jakarta.validation.constraints.NotBlank String muscleGroup,
            @jakarta.validation.constraints.NotBlank String equipmentType,
            @jakarta.validation.constraints.NotBlank String trainingType,
            @jakarta.validation.constraints.NotBlank String loggingMode,
            @jakarta.validation.constraints.Size(max = 2000) String instructions) {}

    @Transactional(readOnly = true)
    public Page<ExerciseDto> list(
            String search,
            String muscleGroup,
            String equipmentType,
            String trainingType,
            boolean atMyGym,
            int page,
            int size) {
        if (page < 0 || size < 1 || size > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page size");
        var user = access.user();
        var all =
                exercises.findAll().stream()
                        .filter(
                                e ->
                                        !e.getArchived()
                                                && (e.getOwnerId() == null
                                                        || e.getOwnerId().equals(user.getId())))
                        .filter(
                                e ->
                                        e.getName()
                                                .toLowerCase(Locale.ROOT)
                                                .contains(search.toLowerCase(Locale.ROOT)))
                        .filter(
                                e ->
                                        muscleGroup.isBlank()
                                                || (e.getMuscleGroup().equalsIgnoreCase(muscleGroup)
                                                        || (muscleGroup.equalsIgnoreCase("Core")
                                                                && e.getMuscleGroup()
                                                                        .equalsIgnoreCase("Abs"))
                                                        || (muscleGroup.equalsIgnoreCase("Quads")
                                                                && e.getMuscleGroup()
                                                                        .equalsIgnoreCase("Legs"))))
                        .filter(
                                e ->
                                        equipmentType.isBlank()
                                                || equipmentType.equalsIgnoreCase(
                                                        e.getEquipmentType()))
                        .filter(
                                e ->
                                        trainingType.isBlank()
                                                || trainingType.equalsIgnoreCase(
                                                        e.getTrainingType()))
                        .filter(
                                e ->
                                        !atMyGym
                                                || (user.getConnectedGymId() != null
                                                        && e.getEquipment().stream()
                                                                .anyMatch(
                                                                        m ->
                                                                                !m.isDeleted()
                                                                                        && Objects
                                                                                                .equals(
                                                                                                        m
                                                                                                                .getGymId(),
                                                                                                        user
                                                                                                                .getConnectedGymId()))))
                        .sorted(
                                Comparator.comparing(
                                        Exercise::getName, String.CASE_INSENSITIVE_ORDER))
                        .map(ExerciseDto::from)
                        .toList();
        int from = (int) Math.min((long) page * size, all.size());
        return new PageImpl<>(
                all.subList(from, Math.min(from + size, all.size())),
                PageRequest.of(page, size),
                all.size());
    }

    public ExerciseDto get(Long id) {
        return ExerciseDto.from(visible(id));
    }

    private Exercise visible(Long id) {
        var e =
                exercises
                        .findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (e.getOwnerId() != null && !e.getOwnerId().equals(access.user().getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return e;
    }

    private Exercise owned(Long id) {
        var e = visible(id);
        if (!Objects.equals(e.getOwnerId(), access.user().getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return e;
    }

    private void fill(Exercise e, Input i) {
        if (i.name() == null
                || i.name().isBlank()
                || i.name().length() > 150
                || i.muscleGroup() == null
                || i.muscleGroup().isBlank()
                || i.muscleGroup().length() > 50
                || i.equipmentType() == null
                || i.equipmentType().isBlank()
                || i.equipmentType().length() > 50
                || !Set.of("STRENGTH", "CARDIO", "MOBILITY")
                        .contains(i.trainingType() == null ? "" : i.trainingType())
                || !Set.of("WEIGHT_REPS", "REPS", "DURATION", "DISTANCE_DURATION")
                        .contains(i.loggingMode() == null ? "" : i.loggingMode())
                || (i.instructions() != null && i.instructions().length() > 2000))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Supply a name, muscle group, equipment, training type and valid logging mode");
        boolean duplicate =
                exercises.findAll().stream()
                        .anyMatch(
                                x ->
                                        !x.getArchived()
                                                && Objects.equals(x.getOwnerId(), e.getOwnerId())
                                                && !Objects.equals(x.getId(), e.getId())
                                                && x.getName().equalsIgnoreCase(i.name().trim()));
        if (duplicate)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "An exercise with this name already exists");
        e.setName(i.name().trim());
        e.setMuscleGroup(i.muscleGroup().trim());
        e.setEquipmentType(i.equipmentType().trim());
        e.setTrainingType(i.trainingType());
        e.setLoggingMode(i.loggingMode());
        e.setInstructions(i.instructions());
    }

    public ExerciseDto create(Input i) {
        var e = new Exercise();
        e.setOwnerId(access.user().getId());
        fill(e, i);
        return ExerciseDto.from(exercises.save(e));
    }

    public ExerciseDto update(Long id, Input i) {
        var e = owned(id);
        if (!Objects.equals(e.getLoggingMode(), i.loggingMode()))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Create a new exercise to change its logging mode");
        fill(e, i);
        return ExerciseDto.from(exercises.save(e));
    }

    public void archive(Long id) {
        var e = owned(id);
        e.setArchived(true);
        exercises.save(e);
    }
}
