package com.ureclive.urec_live_backend.dto;

import java.util.List;

public class EquipmentUtilizationSummary {
    private Long equipmentId;
    private String code;
    private String name;
    private List<EquipmentUtilizationPoint> utilizationByHour;

    public EquipmentUtilizationSummary() {}

    public EquipmentUtilizationSummary(
            Long equipmentId,
            String code,
            String name,
            List<EquipmentUtilizationPoint> utilizationByHour
    ) {
        this.equipmentId = equipmentId;
        this.code = code;
        this.name = name;
        this.utilizationByHour = utilizationByHour;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<EquipmentUtilizationPoint> getUtilizationByHour() {
        return utilizationByHour;
    }

    public void setUtilizationByHour(List<EquipmentUtilizationPoint> utilizationByHour) {
        this.utilizationByHour = utilizationByHour;
    }
}
