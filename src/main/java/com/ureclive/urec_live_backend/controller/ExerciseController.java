package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.service.ExerciseCatalogService;
import com.ureclive.urec_live_backend.service.ExerciseCatalogService.*;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {
    private final ExerciseCatalogService exercises;

    public ExerciseController(ExerciseCatalogService exercises) {
        this.exercises = exercises;
    }

    @GetMapping
    public Page<ExerciseDto> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String muscleGroup,
            @RequestParam(defaultValue = "") String equipmentType,
            @RequestParam(defaultValue = "") String trainingType,
            @RequestParam(defaultValue = "false") boolean atMyGym,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return exercises.list(
                search, muscleGroup, equipmentType, trainingType, atMyGym, page, size);
    }

    @GetMapping("/{id}")
    public ExerciseDto get(@PathVariable Long id) {
        return exercises.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseDto create(@jakarta.validation.Valid @RequestBody Input input) {
        return exercises.create(input);
    }

    @PutMapping("/{id}")
    public ExerciseDto update(
            @PathVariable Long id, @jakarta.validation.Valid @RequestBody Input input) {
        return exercises.update(id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable Long id) {
        exercises.archive(id);
    }
}
