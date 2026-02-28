package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.AdminEquipmentResponse;
import com.ureclive.urec_live_backend.dto.CreateEquipmentRequest;
import com.ureclive.urec_live_backend.dto.UpdateEquipmentRequest;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AdminEquipmentService {

    private final EquipmentRepository equipmentRepository;

    @Autowired
    public AdminEquipmentService(EquipmentRepository equipmentRepository) {
        this.equipmentRepository = equipmentRepository;
    }

    /**
     * Returns paginated equipment list. Optionally filters by name.
     */
    public Page<AdminEquipmentResponse> getAll(int page, int size, String name) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        if (name != null && !name.isBlank()) {
            return equipmentRepository.findAllByDeletedFalseAndNameContainingIgnoreCase(name, pageable)
                    .map(AdminEquipmentResponse::from);
        }
        return equipmentRepository.findAllByDeletedFalse(pageable)
                .map(AdminEquipmentResponse::from);
    }

    /**
     * Creates a new equipment record with status "Available" by default.
     */
    public AdminEquipmentResponse create(CreateEquipmentRequest request) {
        String status = (request.getStatus() != null && !request.getStatus().isBlank())
                ? request.getStatus() : "Available";
        Equipment equipment = new Equipment(null, request.getName(), status, request.getImageUrl());
        equipment = equipmentRepository.save(equipment);
        return AdminEquipmentResponse.from(equipment);
    }

    /**
     * Updates mutable fields on an existing equipment record.
     */
    public AdminEquipmentResponse update(Long id, UpdateEquipmentRequest request) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found with id: " + id));

        if (request.getName() != null && !request.getName().isBlank()) {
            equipment.setName(request.getName());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            equipment.setStatus(request.getStatus());
        }
        if (request.getImageUrl() != null) {
            equipment.setImageUrl(request.getImageUrl());
        }

        equipment = equipmentRepository.save(equipment);
        return AdminEquipmentResponse.from(equipment);
    }

    /**
     * Soft-deletes equipment by setting deleted = true.
     */
    public void softDelete(Long id) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found with id: " + id));
        equipment.setDeleted(true);
        equipmentRepository.save(equipment);
    }

    /**
     * Generates a unique QR code string and assigns it to the equipment record.
     * Returns a short uppercase alphanumeric code the frontend can render as a QR image.
     */
    public AdminEquipmentResponse generateQrCode(Long id) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found with id: " + id));

        String code = generateUniqueCode();
        equipment.setCode(code);
        equipment = equipmentRepository.save(equipment);
        return AdminEquipmentResponse.from(equipment);
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        } while (equipmentRepository.findByCode(code).isPresent());
        return code;
    }
}
