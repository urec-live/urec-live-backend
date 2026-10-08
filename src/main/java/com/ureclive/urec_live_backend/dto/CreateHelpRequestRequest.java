package com.ureclive.urec_live_backend.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/help-requests. Identify the machine by id or by its QR code (the workout
 * tracker only knows the code); the service rejects a request with neither (400).
 */
public class CreateHelpRequestRequest {

    private Long equipmentId;

    @Size(max = 50, message = "Equipment code is too long")
    private String equipmentCode;

    /** Optional: the exercise the member is attempting, used to pick the how-to video. */
    @Size(max = 100, message = "Exercise name is too long")
    private String exerciseName;

    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }

    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }

    public String getExerciseName() { return exerciseName; }
    public void setExerciseName(String exerciseName) { this.exerciseName = exerciseName; }
}
