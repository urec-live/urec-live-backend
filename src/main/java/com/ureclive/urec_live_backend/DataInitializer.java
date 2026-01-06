package com.ureclive.urec_live_backend;

import com.ureclive.urec_live_backend.entity.Machine;
import com.ureclive.urec_live_backend.repository.MachineRepository;
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
        // Clear existing machines to reseed with updated data
        machineRepository.deleteAll();
        
        // 🏋️ CHEST EXERCISES
            // Bench Press
            machineRepository.save(new Machine("BP001", "Bench Press Machine 1", "Available", "Bench Press"));
            machineRepository.save(new Machine("BP002", "Bench Press Machine 2", "Available", "Bench Press"));
            
            // Incline Dumbbell Press
            machineRepository.save(new Machine("IDP001", "Incline Bench 1", "Available", "Incline Dumbbell Press"));
            machineRepository.save(new Machine("IDP002", "Incline Bench 2", "Available", "Incline Dumbbell Press"));
            
            // Cable Fly
            machineRepository.save(new Machine("CF001", "Cable Station 1", "Available", "Cable Fly"));
            machineRepository.save(new Machine("CF002", "Cable Station 2", "Available", "Cable Fly"));
            
            // Push-ups
            machineRepository.save(new Machine("PU001", "Floor Space 1", "Available", "Push-ups"));

            // 💪 BACK EXERCISES
            // Barbell Row
            machineRepository.save(new Machine("BR001", "Barbell Row Machine 1", "Available", "Barbell Row"));
            machineRepository.save(new Machine("BR002", "Seated Cable Row 2", "Available", "Barbell Row"));

            // 💪 SHOULDER EXERCISES
            // Overhead Press
            machineRepository.save(new Machine("OP001", "Shoulder Press 1", "Available", "Overhead Press"));
            machineRepository.save(new Machine("OP002", "Shoulder Press 2", "Available", "Overhead Press"));
            
            // Lateral Raises
            machineRepository.save(new Machine("LR001", "Dumbbell Rack 1", "Available", "Lateral Raises"));

            // 🦵 LEG EXERCISES
            // Leg Press
            machineRepository.save(new Machine("LP001", "Leg Press 1", "Available", "Leg Press"));
            machineRepository.save(new Machine("LP002", "Leg Press 2", "Available", "Leg Press"));

            // 🏃 CARDIO
            machineRepository.save(new Machine("TM001", "Treadmill 1", "Available", "Cardio"));
            machineRepository.save(new Machine("EL001", "Elliptical 2", "Available", "Cardio"));

            System.out.println("✅ Loaded exercise-specific machine data into database.");
    }
}
