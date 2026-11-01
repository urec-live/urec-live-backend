package com.ureclive.urec_live_backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ureclive.urec_live_backend.entity.*;
import com.ureclive.urec_live_backend.repository.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

class StandaloneWorkoutIntegrationTest extends ApiIntegrationTestSupport {
    @Autowired UserRepository users;
    @Autowired GymRepository gyms;
    @Autowired ExerciseRepository exercises;
    @Autowired WorkoutSessionRepository sessions;

    String independent(String name) {
        String token = tokenFor(name, "ROLE_USER");
        var u = users.findByUsername(name).orElseThrow();
        u.setConnectedGymId(null);
        users.save(u);
        return token;
    }

    long custom(String token, String name, String mode) throws Exception {
        var result =
                mvc.perform(
                                json(
                                                post("/api/exercises"),
                                                fields(
                                                        "name",
                                                        name,
                                                        "muscleGroup",
                                                        "Core",
                                                        "equipmentType",
                                                        "BODYWEIGHT",
                                                        "trainingType",
                                                        "STRENGTH",
                                                        "loggingMode",
                                                        mode,
                                                        "instructions",
                                                        "Move with control."))
                                        .header("Authorization", bearer(token)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    Map<String, Object> workout(long exercise, String client, String mode) {
        var set =
                switch (mode) {
                    case "REPS" -> fields("reps", 12);
                    case "DURATION" -> fields("durationSeconds", 45);
                    case "DISTANCE_DURATION" ->
                            fields("distanceMeters", 1000, "durationSeconds", 300);
                    default -> fields("reps", 8, "weightLbs", 50);
                };
        return fields(
                "exerciseId",
                exercise,
                "clientSessionId",
                client,
                "muscleGroup",
                "Core",
                "startTime",
                1000,
                "endTime",
                301000,
                "setDetails",
                List.of(set));
    }

    @Autowired com.ureclive.urec_live_backend.CatalogInitializer initializer;

    @Test
    void catalogSeedingIsRepeatableAndIncludesReviewedInstructions() throws Exception {
        long count = exercises.count();
        initializer.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));
        assertEquals(count, exercises.count());
        assertTrue(
                exercises.findAll().stream()
                                .filter(e -> e.getOwnerId() == null)
                                .filter(
                                        e ->
                                                e.getInstructions() != null
                                                        && !e.getInstructions().isBlank())
                                .count()
                        >= 150);
    }

    @Test
    void completedMachineSessionsCanSyncAfterDisconnectButNotFromAnUnselectedGym()
            throws Exception {
        String token = independent("standalone-offline-machine");
        var exercise = exercises.save(new Exercise("Offline replay weighted", "Core", null));
        var equipment = machine("GYM-OFFLINE-REPLAY", "Offline replay", false);
        equipment.addExercise(exercise);
        equipmentRepository.save(equipment);
        mvc.perform(
                        json(
                                        put("/api/users/me/gym"),
                                        fields(
                                                "gymId",
                                                gyms.findByName("UREC").orElseThrow().getId()))
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/users/me/gym").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        var body = workout(exercise.getId(), "offline-machine-replay", "WEIGHT_REPS");
        body.put("machineCode", equipment.getCode());
        mvc.perform(json(post("/api/sessions"), body).header("Authorization", bearer(token)))
                .andExpect(status().isCreated());
        String other = independent("standalone-never-connected");
        mvc.perform(json(post("/api/sessions"), body).header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void directoryAndConnectionAreOptionalAndPersistent() throws Exception {
        String token = independent("standalone-gym");
        mvc.perform(get("/api/users/me/gym").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
        mvc.perform(get("/api/gyms").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.hasItem("UREC")));
        mvc.perform(
                        json(
                                        put("/api/users/me/gym"),
                                        fields(
                                                "gymId",
                                                gyms.findByName("UREC").orElseThrow().getId()))
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/users/me/gym").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        assertNull(users.findByUsername("standalone-gym").orElseThrow().getConnectedGymId());
    }

    @Test
    void broadCatalogDoesNotNeedGymAndCanFilter() throws Exception {
        String token = independent("standalone-catalog");
        mvc.perform(get("/api/exercises?size=200").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(org.hamcrest.Matchers.greaterThanOrEqualTo(150)));
        mvc.perform(
                        get("/api/exercises?search=Plank&trainingType=STRENGTH")
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].loggingMode").value("DURATION"));
        mvc.perform(get("/api/exercises?atMyGym=true").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void allLoggingModesSaveWithoutMachinesAndRetriesAreIdempotent() throws Exception {
        String token = independent("standalone-log");
        for (String mode : List.of("WEIGHT_REPS", "REPS", "DURATION", "DISTANCE_DURATION")) {
            long id = custom(token, "Standalone " + mode, mode);
            var request = workout(id, "standalone-" + mode, mode);
            var first =
                    mvc.perform(
                                    json(post("/api/sessions"), request)
                                            .header("Authorization", bearer(token)))
                            .andExpect(status().isCreated())
                            .andExpect(jsonPath("$.exerciseId").value(id))
                            .andExpect(jsonPath("$.machineCode").isEmpty())
                            .andReturn();
            long saved =
                    objectMapper
                            .readTree(first.getResponse().getContentAsString())
                            .get("id")
                            .asLong();
            mvc.perform(json(post("/api/sessions"), request).header("Authorization", bearer(token)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(saved));
        }
        assertEquals(4, sessions.countByUser(users.findByUsername("standalone-log").orElseThrow()));
        mvc.perform(get("/api/sessions/me/prs").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void customExercisesArePrivateAndArchivePreservesHistory() throws Exception {
        String owner = independent("standalone-owner"), other = independent("standalone-other");
        long id = custom(owner, "Private timed test", "DURATION");
        mvc.perform(get("/api/exercises/" + id).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/exercises/" + id).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(
                        json(post("/api/sessions"), workout(id, "archive-test", "DURATION"))
                                .header("Authorization", bearer(owner)))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/exercises/" + id).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        mvc.perform(
                        get("/api/exercises?search=Private%20timed%20test")
                                .header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/sessions/me").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.content[0].exerciseName").value("Private timed test"))
                .andExpect(jsonPath("$.content[0].setDetails[0].durationSeconds").value(45));
    }

    @Test
    void cannotWriteAnotherUsersExerciseOrQueue() throws Exception {
        String owner = independent("standalone-private-owner"),
                other = independent("standalone-private-other");
        long id = custom(owner, "Private reps test", "REPS");
        mvc.perform(
                        json(post("/api/sessions"), workout(id, "cross-owner", "REPS"))
                                .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        var request = workout(id, "cross-queue", "REPS");
        request.put("ownerUsername", "someone-else");
        mvc.perform(json(post("/api/sessions"), request).header("Authorization", bearer(owner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossGymAndDisconnectedEquipmentAccessAreDenied() throws Exception {
        String token = independent("standalone-isolation");
        var machine = machine("GYM-ISOLATION", "Gym isolation", false);
        mvc.perform(get("/api/machines/" + machine.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        var gym = new Gym();
        gym.setName("Other test facility");
        gym = gyms.save(gym);
        machine.setGymId(gym.getId());
        equipmentRepository.save(machine);
        mvc.perform(
                        json(
                                        put("/api/users/me/gym"),
                                        fields(
                                                "gymId",
                                                gyms.findByName("UREC").orElseThrow().getId()))
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/machines/" + machine.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        json(
                                        put("/api/machines/" + machine.getId() + "/status"),
                                        fields("status", "In Use"))
                                .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidMetricsAndGymAreRejected() throws Exception {
        String token = independent("standalone-invalid");
        long id = custom(token, "Invalid reps test", "REPS");
        var body = workout(id, "invalid-metric", "WEIGHT_REPS");
        mvc.perform(json(post("/api/sessions"), body).header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        json(put("/api/users/me/gym"), fields("gymId", -999))
                                .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }
}
