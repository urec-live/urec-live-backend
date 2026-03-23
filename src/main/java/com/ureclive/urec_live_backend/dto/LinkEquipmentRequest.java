package com.ureclive.urec_live_backend.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class LinkEquipmentRequest {

    @NotEmpty(message = "At least one equipment ID is required")
    private List<Long> equipmentIds;

    public List<Long> getEquipmentIds() { return equipmentIds; }
    public void setEquipmentIds(List<Long> equipmentIds) { this.equipmentIds = equipmentIds; }
}
