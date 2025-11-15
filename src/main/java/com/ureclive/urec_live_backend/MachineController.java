package com.ureclive.urec_live_backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.Map;

@RestController
@RequestMapping("/api/machines")
@CrossOrigin(origins = "*")
public class MachineController {

    private final MachineRepository machineRepository;

    @Autowired
    public MachineController(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    // ✅ Get all machines
    @GetMapping
    public List<Machine> getAllMachines() {
        return machineRepository.findAll();
    }

    // ✅ Get machines by exercise (e.g., Bench Press)
    @GetMapping("/exercise/{exercise}")
    public List<Machine> getMachinesByExercise(@PathVariable String exercise) {
        return machineRepository.findByExerciseIgnoreCase(exercise);
    }

    // ✅ Get a machine by ID
    @GetMapping("/{id}")
    public Machine getMachineById(@PathVariable @NonNull Long id) {
        Optional<Machine> machine = machineRepository.findById(id);
        return machine.orElseThrow(() -> new RuntimeException("Machine not found with ID: " + id));
    }

    // ✅ Update machine status (accept JSON body)
    @PutMapping("/{id}/status")
    public Machine updateMachineStatus(@PathVariable @NonNull Long id, @RequestBody Map<String, String> body) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Machine not found with ID: " + id));

        String status = body.get("status");
        if (status == null || status.isBlank()) {
            throw new RuntimeException("Missing 'status' in request body");
        }

        machine.setStatus(status);
        return machineRepository.save(machine);
    }
}
