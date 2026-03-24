package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.FloorPlan;

import java.util.List;

public class FloorPlanResponse {

    private Long id;
    private String name;
    private String imageUrl;
    private Integer width;
    private Integer height;
    private List<MachineDTO> equipment;

    public FloorPlanResponse() {}

    public static FloorPlanResponse from(FloorPlan plan, List<MachineDTO> equipment) {
        FloorPlanResponse resp = new FloorPlanResponse();
        resp.id = plan.getId();
        resp.name = plan.getName();
        resp.imageUrl = plan.getImageUrl();
        resp.width = plan.getWidth();
        resp.height = plan.getHeight();
        resp.equipment = equipment;
        return resp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    public List<MachineDTO> getEquipment() { return equipment; }
    public void setEquipment(List<MachineDTO> equipment) { this.equipment = equipment; }
}
