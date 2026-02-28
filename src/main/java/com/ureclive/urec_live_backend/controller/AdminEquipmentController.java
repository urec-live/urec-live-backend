package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.AdminEquipmentResponse;
import com.ureclive.urec_live_backend.dto.CreateEquipmentRequest;
import com.ureclive.urec_live_backend.dto.UpdateEquipmentRequest;
import com.ureclive.urec_live_backend.service.AdminEquipmentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/equipment")
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEquipmentController {

    private static final Logger logger = LoggerFactory.getLogger(AdminEquipmentController.class);

    private final AdminEquipmentService adminEquipmentService;

    @Autowired
    public AdminEquipmentController(AdminEquipmentService adminEquipmentService) {
        this.adminEquipmentService = adminEquipmentService;
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
}
