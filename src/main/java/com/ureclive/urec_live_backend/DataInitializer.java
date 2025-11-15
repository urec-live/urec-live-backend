package com.ureclive.urec_live_backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final MachineRepository machineRepository;

    public DataInitializer(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    @Override
    public void run(String... args) {
        if (machineRepository.count() == 0) {
            // 🏋️ Bench Press
            machineRepository.save(new Machine("Bench Press Machine 1", "Available", "Bench Press"));
            machineRepository.save(new Machine("Bench Press Machine 2", "In Use", "Bench Press"));

            // 💪 Barbell Row
            machineRepository.save(new Machine("Barbell Row Machine 1", "Reserved", "Barbell Row"));
            machineRepository.save(new Machine("Seated Cable Row 2", "Available", "Barbell Row"));

            // 🦵 Leg Press
            machineRepository.save(new Machine("Leg Press 1", "Available", "Leg Press"));
            machineRepository.save(new Machine("Leg Press 2", "Reserved", "Leg Press"));

            // 🏃 Cardio
            machineRepository.save(new Machine("Treadmill 1", "In Use", "Cardio"));
            machineRepository.save(new Machine("Elliptical 2", "Reserved", "Cardio"));

            System.out.println("✅ Loaded exercise-specific machine data into database.");
        } else {
            System.out.println("ℹ️ Machines already exist, skipping seeding.");
        }
    }
}
