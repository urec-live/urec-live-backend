package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.entity.Machine;
import com.ureclive.urec_live_backend.repository.MachineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Map;

@RestController
@RequestMapping("/api/machines")
@CrossOrigin(origins = "*")
public class MachineController {

    private static final Logger logger = LoggerFactory.getLogger(MachineController.class);
    private final MachineRepository machineRepository;

    @Autowired
    public MachineController(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    // ✅ Get all machines
    @GetMapping
    public List<Machine> getAllMachines() {
        logger.info("[GET /api/machines] Fetching all machines");
        List<Machine> machines = machineRepository.findAll();
        logger.info("[GET /api/machines] Returned {} machines", machines.size());
        return machines;
    }

    // ✅ Get machines by exercise (e.g., Bench Press)
    @GetMapping("/exercise/{exercise}")
    public List<Machine> getMachinesByExercise(@PathVariable String exercise) {
        logger.info("[GET /api/machines/exercise/{}] Fetching machines for exercise: {}", exercise, exercise);
        List<Machine> machines = machineRepository.findByExerciseIgnoreCase(exercise);
        logger.info("[GET /api/machines/exercise/{}] Returned {} machines", exercise, machines.size());
        return machines;
    }

    // ✅ Get a machine by ID
    @GetMapping("/{id}")
    public Machine getMachineById(@PathVariable @NonNull Long id) {
        logger.info("[GET /api/machines/{}] Fetching machine by ID: {}", id, id);
        Optional<Machine> machine = machineRepository.findById(id);
        Machine result = machine.orElseThrow(() -> {
            logger.error("[GET /api/machines/{}] Machine not found with ID: {}", id, id);
            return new RuntimeException("Machine not found with ID: " + id);
        });
        logger.info("[GET /api/machines/{}] Returned machine: {} ({})", id, result.getName(), result.getStatus());
        return result;
    }

    // ✅ Update machine status (accept JSON body)
    @PutMapping("/{id}/status")
    public Machine updateMachineStatus(@PathVariable @NonNull Long id, @RequestBody Map<String, String> body) {
        logger.info("[PUT /api/machines/{}/status] Updating machine status for ID: {}", id, id);
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("[PUT /api/machines/{}/status] Machine not found with ID: {}", id, id);
                    return new RuntimeException("Machine not found with ID: " + id);
                });

        String status = body.get("status");
        if (status == null || status.isBlank()) {
            logger.error("[PUT /api/machines/{}/status] Missing 'status' in request body", id);
            throw new RuntimeException("Missing 'status' in request body");
        }

        String oldStatus = machine.getStatus();
        machine.setStatus(status);
        Machine updated = machineRepository.save(machine);
        logger.info("[PUT /api/machines/{}/status] Updated machine {} from '{}' to '{}'", id, machine.getName(), oldStatus, status);
        return updated;
    }

    // ✅ Get machine by code
    @GetMapping("/code/{code}")
    public Machine getMachineByCode(@PathVariable @NonNull String code) {
        logger.info("[GET /api/machines/code/{}] Fetching machine by code: {}", code, code);
        Optional<Machine> machine = machineRepository.findByCode(code);
        Machine result = machine.orElseThrow(() -> {
            logger.error("[GET /api/machines/code/{}] Machine not found with code: {}", code, code);
            return new RuntimeException("Machine not found with code: " + code);
        });
        logger.info("[GET /api/machines/code/{}] Returned machine: {} ({})", code, result.getName(), result.getStatus());
        return result;
    }

    // ✅ Update machine status by code
    @PutMapping("/code/{code}/status")
    public Machine updateMachineStatusByCode(@PathVariable @NonNull String code, @RequestBody Map<String, String> body) {
        logger.info("[PUT /api/machines/code/{}/status] Updating machine status for code: {}", code, code);
        Machine machine = machineRepository.findByCode(code)
                .orElseThrow(() -> {
                    logger.error("[PUT /api/machines/code/{}/status] Machine not found with code: {}", code, code);
                    return new RuntimeException("Machine not found with code: " + code);
                });

        String status = body.get("status");
        if (status == null || status.isBlank()) {
            logger.error("[PUT /api/machines/code/{}/status] Missing 'status' in request body", code);
            throw new RuntimeException("Missing 'status' in request body");
        }

        String oldStatus = machine.getStatus();
        machine.setStatus(status);
        Machine updated = machineRepository.save(machine);
        logger.info("[PUT /api/machines/code/{}/status] Updated machine {} from '{}' to '{}'", code, machine.getName(), oldStatus, status);
        return updated;
    }
}
