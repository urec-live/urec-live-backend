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

    public EquipmentController(EquipmentRepository equipmentRepository, EquipmentSessionRepository sessionRepository) {
        this.equipmentRepository = equipmentRepository;
        this.sessionRepository = sessionRepository;
    }

    @GetMapping("/all")
    public List<EquipmentStatusDTO> getAllEquipmentStatus() {
        List<Equipment> equipmentList = equipmentRepository.findAll();
        return equipmentList.stream()
                .map(equipment -> new EquipmentStatusDTO(
                        equipment.getId(),
                        equipment.getCode(),
                        equipment.getName(),
                        sessionRepository.findByEquipmentAndStatus(equipment, EquipmentSessionStatus.ACTIVE)
                                .isPresent() ? "IN_USE" : "AVAILABLE"
                ))
                .collect(Collectors.toList());
    }
}
