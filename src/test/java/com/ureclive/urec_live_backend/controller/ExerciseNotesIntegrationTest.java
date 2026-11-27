package com.ureclive.urec_live_backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.repository.ExerciseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;

class ExerciseNotesIntegrationTest extends ApiIntegrationTestSupport {
    @Autowired ExerciseRepository exercises;

    @Test void notesPersistUpdateClearAndStayPrivate() throws Exception {
        String alice = tokenFor("exercise-note-alice", "ROLE_USER");
        String bob = tokenFor("exercise-note-bob", "ROLE_USER");
        var exercise = exercises.save(new Exercise("Note test exercise", "Back", null));
        String url = "/api/exercises/" + exercise.getId() + "/notes";
        getAs(alice, url).andExpect(jsonPath("$.note").value(""));
        mvc.perform(json(put(url), fields("note", "  Seat setting 4  ")).header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.note").value("Seat setting 4"));
        getAs(alice, url).andExpect(jsonPath("$.note").value("Seat setting 4"));
        getAs(bob, url).andExpect(jsonPath("$.note").value(""));
        mvc.perform(json(put(url), fields("note", "Updated cue")).header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
        getAs(alice, url).andExpect(jsonPath("$.note").value("Updated cue"));
        mvc.perform(json(put(url), fields("note", "")).header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.note").value(""));
        getAs(alice, url).andExpect(jsonPath("$.note").value(""));
    }

    @Test void rejectsInvalidNotesAndMissingExercises() throws Exception {
        String token = tokenFor("exercise-note-validation", "ROLE_USER");
        var exercise = exercises.save(new Exercise("Note validation", "Back", null));
        String url = "/api/exercises/" + exercise.getId() + "/notes";
        mvc.perform(json(put(url), fields("note", "x".repeat(2001))).header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put(url), fields("note", null)).header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        getAs(token, "/api/exercises/9223372036854775807/notes").andExpect(status().isNotFound());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test void includesAvailableSecondaryMusclesInCatalog() throws Exception {
        String token = tokenFor("exercise-note-metadata", "ROLE_USER");
        var exercise = exercises.findByNameIgnoreCase("Bench Press").orElseThrow();
        getAs(token, "/api/exercises/" + exercise.getId())
                .andExpect(jsonPath("$.secondaryMuscles[0]").value("Triceps"));
    }
}
