package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.FloorPlanResponse;
import com.ureclive.urec_live_backend.dto.MachineDTO;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.entity.FloorPlan;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.FloorPlanRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/floor-plans")
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFloorPlanController {

    private static final Logger logger = LoggerFactory.getLogger(AdminFloorPlanController.class);

    private final FloorPlanRepository floorPlanRepository;
    private final EquipmentRepository equipmentRepository;

    @Autowired
    public AdminFloorPlanController(FloorPlanRepository floorPlanRepository,
                                     EquipmentRepository equipmentRepository) {
        this.floorPlanRepository = floorPlanRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @GetMapping
    public List<FloorPlanResponse> getAll() {
        logger.info("[GET /api/admin/floor-plans] Fetching all floor plans");
        List<FloorPlan> plans = floorPlanRepository.findAllByActiveTrueOrderByFloorNumberAsc();

        return plans.stream().map(plan -> {
            List<MachineDTO> equipment = equipmentRepository
                    .findAllByDeletedFalseAndFloorPlanId(plan.getId())
                    .stream()
                    .map(e -> new MachineDTO(e, getPrimaryExerciseName(e)))
                    .collect(Collectors.toList());
            return FloorPlanResponse.from(plan, equipment);
        }).collect(Collectors.toList());
    }

    @GetMapping("/unassigned-equipment")
    public List<MachineDTO> getUnassignedEquipment() {
        logger.info("[GET /api/admin/floor-plans/unassigned-equipment] Fetching unassigned equipment");
        return equipmentRepository.findAllByDeletedFalseAndFloorPlanIsNull()
                .stream()
                .map(e -> new MachineDTO(e, getPrimaryExerciseName(e)))
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FloorPlanResponse create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Integer width = body.containsKey("width") ? ((Number) body.get("width")).intValue() : 800;
        Integer height = body.containsKey("height") ? ((Number) body.get("height")).intValue() : 600;
        Integer floorNumber = body.containsKey("floorNumber") ? ((Number) body.get("floorNumber")).intValue() : 1;

        logger.info("[POST /api/admin/floor-plans] Creating floor plan: {} (floor {})", name, floorNumber);

        FloorPlan plan = new FloorPlan();
        plan.setName(name);
        plan.setWidth(width);
        plan.setHeight(height);
        plan.setFloorNumber(floorNumber);
        plan.setActive(true);

        FloorPlan saved = floorPlanRepository.save(plan);
        return FloorPlanResponse.from(saved, List.of());
    }

    @PutMapping("/{id}")
    public FloorPlanResponse update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        logger.info("[PUT /api/admin/floor-plans/{}] Updating floor plan", id);
        FloorPlan plan = floorPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Floor plan not found: " + id));

        if (body.containsKey("name")) plan.setName((String) body.get("name"));
        if (body.containsKey("width")) plan.setWidth(((Number) body.get("width")).intValue());
        if (body.containsKey("height")) plan.setHeight(((Number) body.get("height")).intValue());
        if (body.containsKey("floorNumber")) plan.setFloorNumber(((Number) body.get("floorNumber")).intValue());

        FloorPlan saved = floorPlanRepository.save(plan);

        List<MachineDTO> equipment = equipmentRepository
                .findAllByDeletedFalseAndFloorPlanId(saved.getId())
                .stream()
                .map(e -> new MachineDTO(e, getPrimaryExerciseName(e)))
                .collect(Collectors.toList());

        return FloorPlanResponse.from(saved, equipment);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        logger.info("[DELETE /api/admin/floor-plans/{}] Soft-deleting floor plan", id);
        FloorPlan plan = floorPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Floor plan not found: " + id));
        plan.setActive(false);

        // Unassign equipment from this floor
        List<Equipment> equipment = equipmentRepository.findAllByDeletedFalseAndFloorPlanId(id);
        for (Equipment e : equipment) {
            e.setFloorPlan(null);
            e.setFloorX(null);
            e.setFloorY(null);
        }
        equipmentRepository.saveAll(equipment);
        floorPlanRepository.save(plan);
    }

    @PutMapping("/{id}/equipment")
    public FloorPlanResponse updateEquipmentPositions(@PathVariable Long id,
                                                       @RequestBody List<Map<String, Object>> positions) {
        logger.info("[PUT /api/admin/floor-plans/{}/equipment] Bulk updating {} positions", id, positions.size());
        FloorPlan plan = floorPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Floor plan not found: " + id));

        for (Map<String, Object> pos : positions) {
            Long equipmentId = ((Number) pos.get("equipmentId")).longValue();
            Double floorX = ((Number) pos.get("floorX")).doubleValue();
            Double floorY = ((Number) pos.get("floorY")).doubleValue();
            String floorLabel = pos.containsKey("floorLabel") ? (String) pos.get("floorLabel") : null;

            Equipment equipment = equipmentRepository.findById(equipmentId)
                    .orElseThrow(() -> new RuntimeException("Equipment not found: " + equipmentId));
            equipment.setFloorPlan(plan);
            equipment.setFloorX(floorX);
            equipment.setFloorY(floorY);
            if (floorLabel != null) equipment.setFloorLabel(floorLabel);
            equipmentRepository.save(equipment);
        }

        List<MachineDTO> equipment = equipmentRepository
                .findAllByDeletedFalseAndFloorPlanId(plan.getId())
                .stream()
                .map(e -> new MachineDTO(e, getPrimaryExerciseName(e)))
                .collect(Collectors.toList());

        return FloorPlanResponse.from(plan, equipment);
    }

    @DeleteMapping("/{floorId}/equipment/{equipmentId}")
    public void removeEquipmentFromFloor(@PathVariable Long floorId, @PathVariable Long equipmentId) {
        logger.info("[DELETE /api/admin/floor-plans/{}/equipment/{}] Removing equipment from floor", floorId, equipmentId);
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new RuntimeException("Equipment not found: " + equipmentId));
        equipment.setFloorPlan(null);
        equipment.setFloorX(null);
        equipment.setFloorY(null);
        equipmentRepository.save(equipment);
    }

    private String getPrimaryExerciseName(Equipment equipment) {
        return equipment.getExercises().stream()
                .findFirst()
                .map(Exercise::getName)
                .orElse("Unknown");
    }
}
