package com.ureclive.urec_live_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.ExerciseRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public machine endpoints behind the app's Equipment tab and workout screens. Boots the whole
 * app on in-memory H2 (the "test" profile, which also runs the DataInitializer's ~95 seeded
 * machines). Same annotations as the other API tests, so they all share one cached Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MachineApiIntegrationTest {

    // "MF-" codes never clash with the seeded BP001 and friends or the help request tests' "HR-" ones
    private static final String CABLE = "MF-CABLE";
    private static final List<String> CABLE_GROUPS = List.of("Chest", "Triceps");
    private static final List<String> CABLE_EXERCISES = List.of("MF Cable Fly", "MF Crossover", "MF Pushdown");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired @Qualifier("brokerChannel") private AbstractSubscribableChannel brokerChannel;

    private long cableId;

    @BeforeEach
    void cableStationWithAChestAndATricepsExercise() {
        cableId = transactionTemplate.execute(tx -> {
            Equipment cable = equipmentRepository.findByCode(CABLE)
                    .orElseGet(() -> new Equipment(CABLE, "MF Cable Station", "Available", null));
            cable.setStatus("Available");
            link(cable, "MF Cable Fly", "Chest");
            link(cable, "MF Pushdown", "Triceps");
            link(cable, "MF Crossover", "Chest");
            return equipmentRepository.save(cable).getId();
        });
    }

    private void link(Equipment equipment, String exerciseName, String muscleGroup) {
        if (equipment.getExercises().stream().anyMatch(e -> e.getName().equals(exerciseName))) return;
        Exercise exercise = exerciseRepository.findByNameIgnoreCase(exerciseName)
                .orElseGet(() -> exerciseRepository.save(new Exercise(exerciseName, muscleGroup, null)));
        equipment.addExercise(exercise);
    }

    @Test
    void machineListIncludesEachMachinesExercisesAndMuscleGroups() throws Exception {
        JsonNode machines = getJson("/api/machines");

        JsonNode cable = byCode(machines, CABLE);
        assertEquals(CABLE_GROUPS, texts(cable.get("muscleGroups")));
        assertEquals(CABLE_EXERCISES, texts(cable.get("exerciseNames")));
        for (JsonNode machine : machines) {
            assertTrue(machine.get("muscleGroups").isArray(), "every machine has the field: " + machine);
            assertTrue(machine.get("exerciseNames").isArray(), "every machine has the field: " + machine);
        }
    }

    @Test
    void singleMachineAndMachinesForAnExerciseIncludeMuscleGroups() throws Exception {
        assertEquals(CABLE_GROUPS, texts(getJson("/api/machines/" + cableId).get("muscleGroups")));
        assertEquals(CABLE_GROUPS, texts(getJson("/api/machines/code/" + CABLE).get("muscleGroups")));

        // Listed under a non-primary exercise: "exercise" is the one asked for, the groups are all of them
        JsonNode forPushdown = byCode(getJson("/api/machines/exercise/{name}", "MF Pushdown"), CABLE);
        assertEquals("MF Pushdown", forPushdown.get("exercise").asText());
        assertEquals(CABLE_GROUPS, texts(forPushdown.get("muscleGroups")));
        assertEquals(CABLE_EXERCISES, texts(forPushdown.get("exerciseNames")));
    }

    @Test
    void floorPlanMachinesIncludeMuscleGroups() throws Exception {
        List<String> seen = new ArrayList<>();
        for (JsonNode floor : getJson("/api/machines/floor-plans")) {
            for (JsonNode machine : floor.get("equipment")) {
                assertTrue(machine.get("muscleGroups").isArray(), "every floor machine has the field: " + machine);
                seen.addAll(texts(machine.get("muscleGroups")));
            }
        }
        assertFalse(seen.isEmpty(), "the seeded machines are linked to exercises, so some groups show up");
    }

    @Test
    void statusChangeResponseAndBroadcastCarryMuscleGroups() throws Exception {
        List<Message<?>> broadcasts = new CopyOnWriteArrayList<>();
        ChannelInterceptor capture = new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                if ("/topic/machines".equals(SimpMessageHeaderAccessor.getDestination(message.getHeaders()))) {
                    broadcasts.add(message);
                }
                return message;
            }
        };
        brokerChannel.addInterceptor(capture);
        JsonNode response;
        try {
            response = objectMapper.readTree(mvc.perform(put("/api/machines/code/{code}/status", CABLE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"In Use\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
        } finally {
            brokerChannel.removeInterceptor(capture);
        }

        assertEquals("In Use", response.get("status").asText());
        assertEquals(CABLE_GROUPS, texts(response.get("muscleGroups")));

        // What the app's live updates receive, so a patched machine keeps its groups
        assertEquals(1, broadcasts.size());
        JsonNode broadcast = objectMapper.readTree((byte[]) broadcasts.get(0).getPayload());
        assertEquals(CABLE, broadcast.get("code").asText());
        assertEquals("In Use", broadcast.get("status").asText());
        assertEquals(CABLE_GROUPS, texts(broadcast.get("muscleGroups")));
        assertEquals(CABLE_EXERCISES, texts(broadcast.get("exerciseNames")));
    }

    /**
     * Each machine's exercises are a lazy collection. Without batch fetching, listing N machines ran
     * 1 + N queries, which on Neon took ~1.7 s for the 95 seeded machines.
     */
    @Test
    void machineListAndFloorPlansUseAFixedNumberOfQueries() throws Exception {
        long machines = equipmentRepository.count();
        assertTrue(machines >= 50, "needs the seeded machines to mean anything, found " + machines);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            // A handful at most; an async activity-log write from another test may land in the window
            long listQueries = queriesFor(statistics, "/api/machines");
            assertTrue(listQueries < 10, "GET /api/machines ran " + listQueries + " queries for " + machines + " machines");

            long floorQueries = queriesFor(statistics, "/api/machines/floor-plans");
            assertTrue(floorQueries < 10, "GET /api/machines/floor-plans ran " + floorQueries + " queries for " + machines + " machines");
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private long queriesFor(Statistics statistics, String url) throws Exception {
        statistics.clear();
        mvc.perform(get(url)).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private JsonNode getJson(String urlTemplate, Object... uriVariables) throws Exception {
        String json = mvc.perform(get(urlTemplate, uriVariables))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private static JsonNode byCode(JsonNode machines, String code) {
        for (JsonNode machine : machines) {
            if (code.equals(machine.get("code").asText())) return machine;
        }
        return fail(code + " is missing from " + machines);
    }

    private static List<String> texts(JsonNode array) {
        assertNotNull(array, "field is missing");
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
