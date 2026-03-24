package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.entity.FloorPlan;
import com.ureclive.urec_live_backend.dto.FloorPlanResponse;
import com.ureclive.urec_live_backend.dto.MachineDTO;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.ExerciseRepository;
import com.ureclive.urec_live_backend.repository.FloorPlanRepository;
import com.ureclive.urec_live_backend.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final FloorPlanRepository floorPlanRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ActivityLogService activityLogService;

    @Autowired
    public MachineController(EquipmentRepository equipmentRepository,
                             ExerciseRepository exerciseRepository,
                             FloorPlanRepository floorPlanRepository,
                             SimpMessagingTemplate messagingTemplate,
                             ActivityLogService activityLogService) {
        this.equipmentRepository = equipmentRepository;
        this.exerciseRepository = exerciseRepository;
        this.floorPlanRepository = floorPlanRepository;
        this.messagingTemplate = messagingTemplate;
        this.activityLogService = activityLogService;
    }

    // ✅ Get all machines
    @GetMapping
    public List<MachineDTO> getAllMachines() {
        logger.info("[GET /api/machines] Fetching all machines");
        List<Equipment> equipment = equipmentRepository.findAllByDeletedFalse();
        List<MachineDTO> dtos = equipment.stream()
            .map(e -> new MachineDTO(e, getPrimaryExerciseName(e)))
            .collect(Collectors.toList());
        logger.info("[GET /api/machines] Returned {} machines", dtos.size());
        return dtos;
    }

    // ✅ Get all unique muscle groups
    @GetMapping("/muscle-groups")
    public List<String> getMuscleGroups() {
        logger.info("[GET /api/machines/muscle-groups] Fetching all unique muscle groups");
        List<String> muscleGroups = exerciseRepository.findAll().stream()
            .map(Exercise::getMuscleGroup)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        logger.info("[GET /api/machines/muscle-groups] Returned {} muscle groups", muscleGroups.size());
        return muscleGroups;
    }

    // ✅ Get exercises by muscle group
    @GetMapping("/exercises/muscle/{muscleGroup}")
    public List<Exercise> getExercisesByMuscleGroup(@PathVariable String muscleGroup) {
        logger.info("[GET /api/machines/exercises/muscle/{}] Fetching exercises for muscle group: {}", muscleGroup, muscleGroup);
        List<Exercise> exercises = exerciseRepository.findAll().stream()
            .filter(e -> e.getMuscleGroup().equalsIgnoreCase(muscleGroup))
            .collect(Collectors.toList());
        logger.info("[GET /api/machines/exercises/muscle/{}] Returned {} exercises", muscleGroup, exercises.size());
        return exercises;
    }

    // ✅ Get exercises for a specific equipment by ID
    @GetMapping("/{id}/exercises")
    public List<Exercise> getExercisesByEquipmentId(@PathVariable Long id) {
        logger.info("[GET /api/machines/{}/exercises] Fetching exercises for equipment ID: {}", id, id);
        Optional<Equipment> equipment = equipmentRepository.findById(id);
        Equipment eq = equipment.orElseThrow(() -> {
            logger.error("[GET /api/machines/{}/exercises] Equipment not found with ID: {}", id, id);
            return new RuntimeException("Equipment not found with ID: " + id);
        });
        List<Exercise> exercises = new java.util.ArrayList<>(eq.getExercises());
        logger.info("[GET /api/machines/{}/exercises] Returned {} exercises", id, exercises.size());
        return exercises;
    }

    // ✅ Get exercises for a specific equipment by code
    @GetMapping("/code/{code}/exercises")
    public List<Exercise> getExercisesByEquipmentCode(@PathVariable String code) {
        logger.info("[GET /api/machines/code/{}/exercises] Fetching exercises for equipment code: {}", code, code);
        Optional<Equipment> equipment = equipmentRepository.findByCode(code);
        Equipment eq = equipment.orElseThrow(() -> {
            logger.error("[GET /api/machines/code/{}/exercises] Equipment not found with code: {}", code, code);
            return new RuntimeException("Equipment not found with code: " + code);
        });
        List<Exercise> exercises = new java.util.ArrayList<>(eq.getExercises());
        logger.info("[GET /api/machines/code/{}/exercises] Returned {} exercises", code, exercises.size());
        return exercises;
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

        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName() : "anonymous";
        String eventType = "In Use".equalsIgnoreCase(status) ? "CHECK_IN" : "CHECK_OUT";
        activityLogService.log(eventType, username,
                eventType + " on " + equipment.getName() + " (status: " + status + ")",
                equipment.getName());

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

        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName() : "anonymous";
        String eventType = "In Use".equalsIgnoreCase(status) ? "CHECK_IN" : "CHECK_OUT";
        activityLogService.log(eventType, username,
                eventType + " on " + equipment.getName() + " (status: " + status + ")",
                equipment.getName());

        return dto;
    }

    // ✅ Get active floor plan with all equipment positions and statuses
    @GetMapping("/floor-plan")
    public org.springframework.http.ResponseEntity<FloorPlanResponse> getFloorPlan() {
        logger.info("[GET /api/machines/floor-plan] Fetching active floor plan");
        Optional<FloorPlan> optPlan = floorPlanRepository.findByActiveTrue();
        if (optPlan.isEmpty()) {
            // Return a default virtual floor plan with all equipment
            FloorPlan defaultPlan = new FloorPlan();
            defaultPlan.setId(0L);
            defaultPlan.setName("Gym Floor");
            defaultPlan.setWidth(800);
            defaultPlan.setHeight(600);
            defaultPlan.setActive(true);

            List<MachineDTO> allEquipment = equipmentRepository.findAllByDeletedFalse().stream()
                    .map(e -> new MachineDTO(e, getPrimaryExerciseName(e)))
                    .collect(Collectors.toList());
            return org.springframework.http.ResponseEntity.ok(FloorPlanResponse.from(defaultPlan, allEquipment));
        }

        FloorPlan plan = optPlan.get();
        List<MachineDTO> allEquipment = equipmentRepository.findAllByDeletedFalse().stream()
                .map(e -> new MachineDTO(e, getPrimaryExerciseName(e)))
                .collect(Collectors.toList());
        return org.springframework.http.ResponseEntity.ok(FloorPlanResponse.from(plan, allEquipment));
    }

    // Helper method to get primary exercise name
    private String getPrimaryExerciseName(Equipment equipment) {
        return equipment.getExercises().stream()
            .findFirst()
            .map(Exercise::getName)
            .orElse("Unknown");
    }
}
