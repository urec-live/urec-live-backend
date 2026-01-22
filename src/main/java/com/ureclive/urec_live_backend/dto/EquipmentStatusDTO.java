package com.ureclive.urec_live_backend.dto;

public class EquipmentStatusDTO {
    private Long equipmentId;
    private String code;
    private String name;
    private String status;

    public EquipmentStatusDTO() {}

    public EquipmentStatusDTO(Long equipmentId, String code, String name, String status) {
        this.equipmentId = equipmentId;
        this.code = code;
        this.name = name;
        this.status = status;
    }

    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
