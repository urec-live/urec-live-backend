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
@PreAuthorize("hasAuthority('ADMIN') or hasAuthority('ROLE_ADMIN')")
public class AdminFloorPlanController {
    @Autowired private com.ureclive.urec_live_backend.service.FloorLayoutValidator layoutValidator;
    @Autowired private com.ureclive.urec_live_backend.service.UrecAdminScope facility;

    @org.springframework.beans.factory.annotation.Autowired
    private com.ureclive.urec_live_backend.repository.GymRepository gymRepository;

    private static final Logger logger = LoggerFactory.getLogger(AdminFloorPlanController.class);

    private final FloorPlanRepository floorPlanRepository;
    private final EquipmentRepository equipmentRepository;

    @Autowired
    public AdminFloorPlanController(
            FloorPlanRepository floorPlanRepository, EquipmentRepository equipmentRepository) {
        this.floorPlanRepository = floorPlanRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @GetMapping
    public List<FloorPlanResponse> getAll() {
        logger.info("[GET /api/admin/floor-plans] Fetching all floor plans");
        List<FloorPlan> plans = floorPlanRepository.findAllByActiveTrueOrderByFloorNumberAsc();

        return plans.stream()
                .filter(f -> facility.includes(f.getGymId()))
                .map(
                        plan -> {
                            List<MachineDTO> equipment =
                                    equipmentRepository
                                            .findAllByDeletedFalseAndFloorPlanId(plan.getId())
                                            .stream()
                                            .map(e -> new MachineDTO(e, getPrimaryExerciseName(e)))
                                            .collect(Collectors.toList());
                            return FloorPlanResponse.from(plan, equipment);
                        })
                .collect(Collectors.toList());
    }

    @PutMapping("/{id}/layout")
    @org.springframework.transaction.annotation.Transactional
    public FloorPlanResponse updateLayout(@PathVariable Long id,
            @RequestBody com.ureclive.urec_live_backend.dto.FloorLayout layout) {
        FloorPlan plan = floorPlanRepository.findByIdAndActiveTrue(id)
                .filter(f -> facility.includes(f.getGymId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
        layoutValidator.validate(layout, plan.getWidth(), plan.getHeight());
        plan.setLayout(layout);
        floorPlanRepository.save(plan);
        return FloorPlanResponse.from(plan, equipmentRepository.findAllByDeletedFalseAndFloorPlanId(id)
                .stream().map(e -> new MachineDTO(e, getPrimaryExerciseName(e))).toList());
    }

    @GetMapping("/unassigned-equipment")
    public List<MachineDTO> getUnassignedEquipment() {
        logger.info(
                "[GET /api/admin/floor-plans/unassigned-equipment] Fetching unassigned equipment");
        return equipmentRepository.findAllByDeletedFalseAndFloorPlanIsNull().stream()
                .filter(e -> facility.includes(e.getGymId()))
                .map(e -> new MachineDTO(e, getPrimaryExerciseName(e)))
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FloorPlanResponse create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Integer width = body.containsKey("width") ? ((Number) body.get("width")).intValue() : 800;
        Integer height =
                body.containsKey("height") ? ((Number) body.get("height")).intValue() : 600;
        Integer floorNumber =
                body.containsKey("floorNumber") ? ((Number) body.get("floorNumber")).intValue() : 1;

        logger.info(
                "[POST /api/admin/floor-plans] Creating floor plan: {} (floor {})",
                name,
                floorNumber);

        FloorPlan plan = new FloorPlan();
        plan.setGymId(gymRepository.findByName("UREC").orElseThrow().getId());
        plan.setName(name);
        plan.setWidth(width);
        plan.setHeight(height);
        plan.setFloorNumber(floorNumber);
        plan.setActive(true);

        layoutValidator.validate(plan.getLayout(), width, height);
        FloorPlan saved = floorPlanRepository.save(plan);
        return FloorPlanResponse.from(saved, List.of());
    }

    @PutMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public FloorPlanResponse update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        logger.info("[PUT /api/admin/floor-plans/{}] Updating floor plan", id);
        FloorPlan plan =
                floorPlanRepository
                        .findById(id)
                        .filter(f -> facility.includes(f.getGymId()))
                        .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Floor plan not found"));

        if (body.containsKey("name")) plan.setName((String) body.get("name"));
        if (body.containsKey("width")) plan.setWidth(((Number) body.get("width")).intValue());
        if (body.containsKey("height")) plan.setHeight(((Number) body.get("height")).intValue());
        if (body.containsKey("floorNumber"))
            plan.setFloorNumber(((Number) body.get("floorNumber")).intValue());

        layoutValidator.validate(plan.getLayout(), plan.getWidth(), plan.getHeight());
        for (Equipment e : equipmentRepository.findAllByDeletedFalseAndFloorPlanId(id)) {
            if (e.getFloorX() != null && e.getFloorY() != null)
                layoutValidator.point(e.getFloorX(), e.getFloorY(), plan.getWidth(), plan.getHeight());
        }
        FloorPlan saved = floorPlanRepository.save(plan);

        List<MachineDTO> equipment =
                equipmentRepository.findAllByDeletedFalseAndFloorPlanId(saved.getId()).stream()
                        .map(e -> new MachineDTO(e, getPrimaryExerciseName(e)))
                        .collect(Collectors.toList());

        return FloorPlanResponse.from(saved, equipment);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        logger.info("[DELETE /api/admin/floor-plans/{}] Soft-deleting floor plan", id);
        FloorPlan plan =
                floorPlanRepository
                        .findById(id)
                        .filter(f -> facility.includes(f.getGymId()))
                        .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Floor plan not found"));
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

    public record EquipmentMapPosition(Long equipmentId, Double floorX, Double floorY, String floorLabel) {}

    @PutMapping("/{id}/equipment")
    @org.springframework.transaction.annotation.Transactional
    public FloorPlanResponse updateEquipmentPositions(
            @PathVariable Long id, @RequestBody List<EquipmentMapPosition> positions) {
        logger.info(
                "[PUT /api/admin/floor-plans/{}/equipment] Bulk updating {} positions",
                id,
                positions.size());
        FloorPlan plan =
                floorPlanRepository
                        .findById(id)
                        .filter(f -> facility.includes(f.getGymId()))
                        .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Floor plan not found"));

        layoutValidator.check(positions.size() <= 1000, "At most 1000 positions");
        for (EquipmentMapPosition pos : positions) {
            layoutValidator.check(pos != null && pos.equipmentId() != null, "Equipment ID is required");
            Long equipmentId = pos.equipmentId();
            Double floorX = pos.floorX();
            Double floorY = pos.floorY();
            String floorLabel = pos.floorLabel();

            Equipment equipment =
                    equipmentRepository
                            .findById(equipmentId)
                            .filter(e -> facility.includes(e.getGymId()))
                            .orElseThrow(
                                    () ->
                                            new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));
            layoutValidator.check(java.util.Objects.equals(equipment.getGymId(), plan.getGymId()) && !equipment.isDeleted() && Boolean.TRUE.equals(plan.getActive()), "Equipment and active floor must belong to the same gym");
            layoutValidator.point(floorX, floorY, plan.getWidth(), plan.getHeight());
            layoutValidator.check(floorLabel == null || floorLabel.length() <= 50, "Label is too long");
            equipment.setFloorPlan(plan);
            equipment.setFloorX(floorX);
            equipment.setFloorY(floorY);
            if (floorLabel != null) equipment.setFloorLabel(floorLabel);
            equipmentRepository.save(equipment);
        }

        List<MachineDTO> equipment =
                equipmentRepository.findAllByDeletedFalseAndFloorPlanId(plan.getId()).stream()
                        .map(e -> new MachineDTO(e, getPrimaryExerciseName(e)))
                        .collect(Collectors.toList());

        return FloorPlanResponse.from(plan, equipment);
    }

    @DeleteMapping("/{floorId}/equipment/{equipmentId}")
    public void removeEquipmentFromFloor(
            @PathVariable Long floorId, @PathVariable Long equipmentId) {
        logger.info(
                "[DELETE /api/admin/floor-plans/{}/equipment/{}] Removing equipment from floor",
                floorId,
                equipmentId);
        Equipment equipment =
                equipmentRepository
                        .findById(equipmentId)
                        .filter(e -> facility.includes(e.getGymId()))
                        .orElseThrow(
                                () -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));
        layoutValidator.check(equipment.getFloorPlan() != null && equipment.getFloorPlan().getId().equals(floorId), "Equipment is not on this floor");
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
