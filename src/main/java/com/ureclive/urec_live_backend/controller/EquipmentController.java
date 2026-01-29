package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.EquipmentStatusDTO;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentSessionStatus;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.EquipmentSessionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentSessionRepository sessionRepository;
    private final com.ureclive.urec_live_backend.service.EquipmentService equipmentService;

    public EquipmentController(EquipmentRepository equipmentRepository, EquipmentSessionRepository sessionRepository,
            com.ureclive.urec_live_backend.service.EquipmentService equipmentService) {
        this.equipmentRepository = equipmentRepository;
        this.sessionRepository = sessionRepository;
        this.equipmentService = equipmentService;
    }

    @GetMapping("/all")
    public List<EquipmentStatusDTO> getAllEquipmentStatus(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Gym-Id", defaultValue = "1") Long gymId) {
        // Default to Gym 1 (migrated storage) if no header.

        List<Equipment> equipmentList = equipmentRepository.findByLocationId(gymId);
        return equipmentList.stream()
                .map(equipment -> {
                    String dbStatus = equipment.getStatus();
                    if (!"AVAILABLE".equalsIgnoreCase(dbStatus)) {
                        return new EquipmentStatusDTO(equipment.getId(), equipment.getCode(), equipment.getName(),
                                dbStatus);
                    }
                    boolean inUse = sessionRepository
                            .findByEquipmentIdAndStatus(equipment.getId(), EquipmentSessionStatus.ACTIVE)
                            .isPresent();
                    return new EquipmentStatusDTO(
                            equipment.getId(),
                            equipment.getCode(),
                            equipment.getName(),
                            inUse ? "IN_USE" : "AVAILABLE");
                })
                .collect(Collectors.toList());
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/status")
    public org.springframework.http.ResponseEntity<Void> updateEquipmentStatus(
            @org.springframework.web.bind.annotation.PathVariable long id,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> payload) {
        String newStatus = payload.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }

        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Equipment not found"));
        equipment.setStatus(newStatus);
        equipmentRepository.save(equipment);

        return org.springframework.http.ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/recommendations")
    public org.springframework.http.ResponseEntity<List<com.ureclive.urec_live_backend.dto.EquipmentWaitTimeEstimate>> getRecommendations(
            @org.springframework.web.bind.annotation.PathVariable long id) {
        return org.springframework.http.ResponseEntity.ok(equipmentService.getRecommendations(id));
    }
}
