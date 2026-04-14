package com.ureclive.urec_live_backend.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ureclive.urec_live_backend.dto.AdminEquipmentResponse;
import com.ureclive.urec_live_backend.dto.CreateEquipmentRequest;
import com.ureclive.urec_live_backend.dto.UpdateEquipmentRequest;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.FloorPlan;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.FloorPlanRepository;
import com.ureclive.urec_live_backend.service.AdminEquipmentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/equipment")
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEquipmentController {

    private static final Logger logger = LoggerFactory.getLogger(AdminEquipmentController.class);

    private final AdminEquipmentService adminEquipmentService;
    private final EquipmentRepository equipmentRepository;
    private final FloorPlanRepository floorPlanRepository;

    @Autowired
    public AdminEquipmentController(AdminEquipmentService adminEquipmentService,
                                     EquipmentRepository equipmentRepository,
                                     FloorPlanRepository floorPlanRepository) {
        this.adminEquipmentService = adminEquipmentService;
        this.equipmentRepository = equipmentRepository;
        this.floorPlanRepository = floorPlanRepository;
    }

    /** GET /api/admin/equipment?page=0&size=20&name=bench */
    @GetMapping
    public Page<AdminEquipmentResponse> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name) {
        logger.info("[GET /api/admin/equipment] page={} size={} name={}", page, size, name);
        return adminEquipmentService.getAll(page, size, name);
    }

    /** POST /api/admin/equipment */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminEquipmentResponse create(@Valid @RequestBody CreateEquipmentRequest request) {
        logger.info("[POST /api/admin/equipment] Creating equipment: {}", request.getName());
        return adminEquipmentService.create(request);
    }

    /** PUT /api/admin/equipment/{id} */
    @PutMapping("/{id}")
    public AdminEquipmentResponse update(@PathVariable Long id,
                                         @RequestBody UpdateEquipmentRequest request) {
        logger.info("[PUT /api/admin/equipment/{}] Updating equipment", id);
        return adminEquipmentService.update(id, request);
    }

    /** DELETE /api/admin/equipment/{id} — soft delete */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        logger.info("[DELETE /api/admin/equipment/{}] Soft-deleting equipment", id);
        adminEquipmentService.softDelete(id);
    }

    /** POST /api/admin/equipment/{id}/qr — generate and assign QR code */
    @PostMapping("/{id}/qr")
    public AdminEquipmentResponse generateQr(@PathVariable Long id) {
        logger.info("[POST /api/admin/equipment/{}/qr] Generating QR code", id);
        return adminEquipmentService.generateQrCode(id);
    }

    /** PUT /api/admin/equipment/{id}/position — set floor map coordinates */
    @PutMapping("/{id}/position")
    public Map<String, Object> updatePosition(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        logger.info("[PUT /api/admin/equipment/{}/position] Updating floor position", id);
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Equipment not found: " + id));

        if (body.containsKey("floorX")) {
            equipment.setFloorX(((Number) body.get("floorX")).doubleValue());
        }
        if (body.containsKey("floorY")) {
            equipment.setFloorY(((Number) body.get("floorY")).doubleValue());
        }
        if (body.containsKey("floorLabel")) {
            equipment.setFloorLabel((String) body.get("floorLabel"));
        }
        if (body.containsKey("floorPlanId")) {
            Object fpId = body.get("floorPlanId");
            if (fpId != null) {
                FloorPlan plan = floorPlanRepository.findById(((Number) fpId).longValue())
                        .orElseThrow(() -> new RuntimeException("Floor plan not found"));
                equipment.setFloorPlan(plan);
            } else {
                equipment.setFloorPlan(null);
            }
        }
        equipmentRepository.save(equipment);

        return Map.of("id", equipment.getId(),
                "floorX", equipment.getFloorX() != null ? equipment.getFloorX() : 0,
                "floorY", equipment.getFloorY() != null ? equipment.getFloorY() : 0,
                "floorLabel", equipment.getFloorLabel() != null ? equipment.getFloorLabel() : "");
    }
}
