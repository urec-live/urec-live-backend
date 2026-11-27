package com.ureclive.urec_live_backend;

import com.ureclive.urec_live_backend.entity.*;
import com.ureclive.urec_live_backend.repository.*;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Component
@Order(100)
public class CatalogInitializer implements ApplicationRunner {
    private final GymRepository gyms;
    private final EquipmentRepository machines;
    private final FloorPlanRepository floors;
    private final ExerciseRepository exercises;

    public CatalogInitializer(
            GymRepository g, EquipmentRepository m, FloorPlanRepository f, ExerciseRepository e) {
        gyms = g;
        machines = m;
        floors = f;
        exercises = e;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        var gym =
                gyms.findByName("UREC")
                        .orElseGet(
                                () -> {
                                    var g = new Gym();
                                    g.setName("UREC");
                                    return gyms.save(g);
                                });
        machines.findAll()
                .forEach(
                        m -> {
                            if (m.getGymId() == null) {
                                m.setGymId(gym.getId());
                                machines.save(m);
                            }
                        });
        floors.findAll()
                .forEach(
                        f -> {
                            if (f.getGymId() == null) {
                                f.setGymId(gym.getId());
                                floors.save(f);
                            }
                        });
        try (var reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ClassPathResource("catalog/exercises.tsv").getInputStream(),
                                StandardCharsets.UTF_8))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                var c = line.split("\t", -1);
                var existing = exercises.findByNameIgnoreCase(c[0]);
                if (existing.isEmpty() && c.length > 6 && !c[6].isBlank()) {
                    for (String alias : c[6].split("\\|")) {
                        existing = exercises.findByNameIgnoreCase(alias);
                        if (existing.isPresent()) break;
                    }
                }
                var e = existing.orElseGet(() -> new Exercise(c[0], c[1], null));
                if (c.length > 7 && !c[7].isBlank() && e.getSecondaryMuscles() == null && e.getOwnerId() == null) {
                    e.setSecondaryMuscles(c[7]);
                    exercises.save(e);
                }
                if (e.getInstructions() == null) {
                    e.setEquipmentType(c[2]);
                    e.setTrainingType(c[3]);
                    e.setLoggingMode(c[4]);
                    e.setInstructions(c[5]);
                    exercises.save(e);
                }
            }
        }
    }
}
