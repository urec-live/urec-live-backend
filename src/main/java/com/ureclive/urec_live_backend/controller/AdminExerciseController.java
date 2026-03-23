package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.AdminExerciseResponse;
import com.ureclive.urec_live_backend.dto.CreateExerciseRequest;
import com.ureclive.urec_live_backend.dto.LinkEquipmentRequest;
import com.ureclive.urec_live_backend.dto.UpdateExerciseRequest;
import com.ureclive.urec_live_backend.service.AdminExerciseService;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/exercises")
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class AdminExerciseController {

    private static final Logger logger = LoggerFactory.getLogger(AdminExerciseController.class);

    private final AdminExerciseService adminExerciseService;

    @Autowired
    public AdminExerciseController(AdminExerciseService adminExerciseService) {
        this.adminExerciseService = adminExerciseService;
    }

    /** GET /api/admin/exercises */
    @GetMapping
    public List<AdminExerciseResponse> getAll() {
        logger.info("[GET /api/admin/exercises] Fetching all exercises");
        return adminExerciseService.getAll();
    }

    /** POST /api/admin/exercises */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminExerciseResponse create(@Valid @RequestBody CreateExerciseRequest request) {
        logger.info("[POST /api/admin/exercises] Creating exercise: {}", request.getName());
        return adminExerciseService.create(request);
    }

    /** PUT /api/admin/exercises/{id} */
    @PutMapping("/{id}")
    public AdminExerciseResponse update(@PathVariable Long id,
                                        @RequestBody UpdateExerciseRequest request) {
        logger.info("[PUT /api/admin/exercises/{}] Updating exercise", id);
        return adminExerciseService.update(id, request);
    }

    /** DELETE /api/admin/exercises/{id} */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        logger.info("[DELETE /api/admin/exercises/{}] Deleting exercise", id);
        adminExerciseService.delete(id);
    }

    /** POST /api/admin/exercises/{id}/equipment — link exercise to equipment */
    @PostMapping("/{id}/equipment")
    public AdminExerciseResponse linkEquipment(@PathVariable Long id,
                                               @Valid @RequestBody LinkEquipmentRequest request) {
        logger.info("[POST /api/admin/exercises/{}/equipment] Linking {} equipment records", id, request.getEquipmentIds().size());
        return adminExerciseService.linkEquipment(id, request);
    }

    /** DELETE /api/admin/exercises/{id}/equipment/{equipmentId} — unlink equipment */
    @DeleteMapping("/{id}/equipment/{equipmentId}")
    public AdminExerciseResponse unlinkEquipment(@PathVariable Long id,
                                                  @PathVariable Long equipmentId) {
        logger.info("[DELETE /api/admin/exercises/{}/equipment/{}] Unlinking equipment", id, equipmentId);
        return adminExerciseService.unlinkEquipment(id, equipmentId);
    }
}
