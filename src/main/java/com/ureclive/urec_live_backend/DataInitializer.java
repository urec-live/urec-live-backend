package com.ureclive.urec_live_backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    private final MachineRepository machineRepository;

    public DataInitializer(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    @Override
    public void run(String... args) {
        List<Machine> seeds = List.of(
                new Machine("Bench Press Machine 1", "Available", "Bench Press"),
                new Machine("Bench Press Machine 2", "In Use", "Bench Press"),
                new Machine("Barbell Row Machine 1", "Reserved", "Barbell Row"),
                new Machine("Seated Cable Row 2", "Available", "Barbell Row"),
                new Machine("Leg Press 1", "Available", "Leg Press"),
                new Machine("Leg Press 2", "Reserved", "Leg Press"),
                new Machine("Treadmill 1", "In Use", "Cardio"),
                new Machine("Elliptical 2", "Reserved", "Cardio")
        );

        for (Machine seed : seeds) {
            Optional<Machine> existing = machineRepository.findByNameIgnoreCase(seed.getName());
            if (existing.isPresent()) {
                Machine machine = existing.get();
                boolean updated = false;
                if (machine.getExercise() == null || machine.getExercise().isBlank()) {
                    machine.setExercise(seed.getExercise());
                    updated = true;
                }
                if (machine.getStatus() == null || machine.getStatus().isBlank()) {
                    machine.setStatus(seed.getStatus());
                    updated = true;
                }
                if (updated) {
                    machineRepository.save(machine);
                }
            } else {
                machineRepository.save(seed);
            }
        }
        System.out.println("✅ Verified exercise-specific machine data in database.");
    }
}
