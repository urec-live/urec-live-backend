package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.dto.MachineDTO;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.ExerciseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/machines")
@CrossOrigin(origins = "*")
public class MachineController {

    private static final Logger logger = LoggerFactory.getLogger(MachineController.class);
    private final EquipmentRepository equipmentRepository;
    private final ExerciseRepository exerciseRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public MachineController(EquipmentRepository equipmentRepository, 
                           ExerciseRepository exerciseRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.equipmentRepository = equipmentRepository;
        this.exerciseRepository = exerciseRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // ✅ Get all machines
    @GetMapping
    public List<MachineDTO> getAllMachines() {
        logger.info("[GET /api/machines] Fetching all machines");
        List<Equipment> equipment = equipmentRepository.findAll();
        List<MachineDTO> dtos = equipment.stream()
            .map(e -> new MachineDTO(e, getPrimaryExerciseName(e)))
            .collect(Collectors.toList());
        logger.info("[GET /api/machines] Returned {} machines", dtos.size());
        return dtos;
    }

    // ✅ Get machines by exercise (e.g., Bench Press)
    @GetMapping("/exercise/{exercise}")
    public List<MachineDTO> getMachinesByExercise(@PathVariable String exercise) {
        logger.info("[GET /api/machines/exercise/{}] Fetching machines for exercise: {}", exercise, exercise);
        List<Equipment> equipment = equipmentRepository.findByExerciseName(exercise);
        List<MachineDTO> dtos = equipment.stream()
            .map(e -> new MachineDTO(e, exercise))
            .collect(Collectors.toList());
        logger.info("[GET /api/machines/exercise/{}] Returned {} machines", exercise, dtos.size());
        return dtos;
    }

    // ✅ Get a machine by ID
    @GetMapping("/{id}")
    public MachineDTO getMachineById(@PathVariable @NonNull Long id) {
        logger.info("[GET /api/machines/{}] Fetching machine by ID: {}", id, id);
        Optional<Equipment> equipment = equipmentRepository.findById(id);
        Equipment eq = equipment.orElseThrow(() -> {
            logger.error("[GET /api/machines/{}] Machine not found with ID: {}", id, id);
            return new RuntimeException("Machine not found with ID: " + id);
        });
        MachineDTO dto = new MachineDTO(eq, getPrimaryExerciseName(eq));
        logger.info("[GET /api/machines/{}] Returned machine: {} ({})", id, dto.getName(), dto.getStatus());
        return dto;
    }

    // ✅ Update machine status (accept JSON body)
    @PutMapping("/{id}/status")
    public MachineDTO updateMachineStatus(@PathVariable @NonNull Long id, @RequestBody Map<String, String> body) {
        logger.info("[PUT /api/machines/{}/status] Updating machine status for ID: {}", id, id);
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("[PUT /api/machines/{}/status] Machine not found with ID: {}", id, id);
                    return new RuntimeException("Machine not found with ID: " + id);
                });

        String status = body.get("status");
        if (status == null || status.isBlank()) {
            logger.error("[PUT /api/machines/{}/status] Missing 'status' in request body", id);
            throw new RuntimeException("Missing 'status' in request body");
        }

        String oldStatus = equipment.getStatus();
        equipment.setStatus(status);
        Equipment updated = equipmentRepository.save(equipment);
        logger.info("[PUT /api/machines/{}/status] Updated machine {} from '{}' to '{}'", id, equipment.getName(), oldStatus, status);
        
        MachineDTO dto = new MachineDTO(updated, getPrimaryExerciseName(updated));
        
        // Broadcast update to all WebSocket clients
        messagingTemplate.convertAndSend("/topic/machines", dto);
        logger.info("[WebSocket] Broadcasted machine update for ID: {}", id);
        
        return dto;
    }

    // ✅ Get machine by code
    @GetMapping("/code/{code}")
    public MachineDTO getMachineByCode(@PathVariable @NonNull String code) {
        logger.info("[GET /api/machines/code/{}] Fetching machine by code: {}", code, code);
        Optional<Equipment> equipment = equipmentRepository.findByCode(code);
        Equipment eq = equipment.orElseThrow(() -> {
            logger.error("[GET /api/machines/code/{}] Machine not found with code: {}", code, code);
            return new RuntimeException("Machine not found with code: " + code);
        });
        MachineDTO dto = new MachineDTO(eq, getPrimaryExerciseName(eq));
        logger.info("[GET /api/machines/code/{}] Returned machine: {} ({})", code, dto.getName(), dto.getStatus());
        return dto;
    }

    // ✅ Update machine status by code
    @PutMapping("/code/{code}/status")
    public MachineDTO updateMachineStatusByCode(@PathVariable @NonNull String code, @RequestBody Map<String, String> body) {
        logger.info("[PUT /api/machines/code/{}/status] Updating machine status for code: {}", code, code);
        Equipment equipment = equipmentRepository.findByCode(code)
                .orElseThrow(() -> {
                    logger.error("[PUT /api/machines/code/{}/status] Machine not found with code: {}", code, code);
                    return new RuntimeException("Machine not found with code: " + code);
                });

        String status = body.get("status");
        if (status == null || status.isBlank()) {
            logger.error("[PUT /api/machines/code/{}/status] Missing 'status' in request body", code);
            throw new RuntimeException("Missing 'status' in request body");
        }

        String oldStatus = equipment.getStatus();
        equipment.setStatus(status);
        Equipment updated = equipmentRepository.save(equipment);
        logger.info("[PUT /api/machines/code/{}/status] Updated machine {} from '{}' to '{}'", code, equipment.getName(), oldStatus, status);
        
        MachineDTO dto = new MachineDTO(updated, getPrimaryExerciseName(updated));
        
        // Broadcast update to all WebSocket clients
        messagingTemplate.convertAndSend("/topic/machines", dto);
        logger.info("[WebSocket] Broadcasted machine update for code: {}", code);
        
        return dto;
    }

    // Helper method to get primary exercise name
    private String getPrimaryExerciseName(Equipment equipment) {
        return equipment.getExercises().stream()
            .findFirst()
            .map(Exercise::getName)
            .orElse("Unknown");
    }
}
