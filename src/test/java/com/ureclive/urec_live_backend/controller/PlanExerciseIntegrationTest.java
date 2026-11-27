package com.ureclive.urec_live_backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import com.ureclive.urec_live_backend.entity.*;
import com.ureclive.urec_live_backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.*;
import java.util.*;

class PlanExerciseIntegrationTest extends ApiIntegrationTestSupport {
    @Autowired ExerciseRepository exercises;
    @Autowired UserRepository users;
    @Autowired WorkoutSessionRepository sessions;

    @Test void savesReloadsReordersCopiesAndCountsExactExercises() throws Exception {
        String token = tokenFor("plan-picker", "ROLE_USER");
        var bench = exercises.save(new Exercise("Plan bench", "Chest", null));
        var fly = exercises.save(new Exercise("Plan fly", "Chest", null));
        var first = fields("exerciseId", bench.getId(), "muscleGroup", "Wrong client muscle", "targetCount", 1);
        var second = fields("exerciseId", fly.getId(), "muscleGroup", "Chest", "targetCount", 1);
        var legacy = fields("muscleGroup", "Back", "targetCount", 2);
        var day = fields("dayOfWeek", LocalDate.now().getDayOfWeek().getValue(), "items", List.of(first, second, legacy));
        var body = fields("name", "Exercise plan", "days", List.of(day));
        var created = mvc.perform(json(post("/api/plans"), body).header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days[0].items[0].muscleGroup").value("Chest"))
                .andExpect(jsonPath("$.days[0].items[0].exerciseName").value("Plan bench"))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        getAs(token, "/api/plans/me")
                .andExpect(jsonPath("$.days[0].items[1].exerciseId").value(fly.getId()));
        day.put("items", List.of(second, first, legacy));
        mvc.perform(json(put("/api/plans/" + id), body).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].items[0].exerciseId").value(fly.getId()));
        mvc.perform(post("/api/plans/" + id + "/copy").header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days[0].items[0].exerciseName").value("Plan fly"));

        var completed = new WorkoutSession();
        completed.setUser(users.findByUsername("plan-picker").orElseThrow());
        completed.setExercise(bench);
        completed.setMuscleGroup("Chest");
        completed.setStartedAt(Instant.now());
        completed.setEndedAt(Instant.now());
        sessions.save(completed);
        var pending = new WorkoutSession();
        pending.setUser(completed.getUser());
        pending.setExercise(fly);
        pending.setMuscleGroup("Chest");
        pending.setStartedAt(Instant.now());
        sessions.save(pending);
        getAs(token, "/api/plans/me/today")
                .andExpect(jsonPath("$.items[0].exerciseName").value("Plan fly"))
                .andExpect(jsonPath("$.items[0].completedCount").value(0))
                .andExpect(jsonPath("$.items[1].completedCount").value(1))
                .andExpect(jsonPath("$.items[2].targetCount").value(2));
    }

    @Test void refusesAnotherMembersCustomExercise() throws Exception {
        String token = tokenFor("plan-owner", "ROLE_USER");
        tokenFor("plan-other", "ROLE_USER");
        var custom = new Exercise("Private plan exercise", "Core", null);
        custom.setOwnerId(users.findByUsername("plan-other").orElseThrow().getId());
        exercises.save(custom);
        var body = fields("name", "Private exercise", "days", List.of(fields("dayOfWeek", 1,
                "items", List.of(fields("exerciseId", custom.getId(), "muscleGroup", "Core", "targetCount", 1)))));
        mvc.perform(json(post("/api/plans"), body).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }
}
