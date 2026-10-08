package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.HelpRequest;
import com.ureclive.urec_live_backend.entity.HelpRequestClosedBy;
import com.ureclive.urec_live_backend.entity.HelpRequestStatus;

import java.time.Instant;
import java.util.List;

/** The member's view of their own help request. Never names the staff member. */
public class HelpRequestResponse {

    private Long id;
    private HelpRequestStatus status;
    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    private String exerciseName;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant firstResponseAt;
    private Instant closedAt;
    private HelpRequestClosedBy closedBy;
    private List<HelpDemoLink> demos;

    public HelpRequestResponse() {}

    public static HelpRequestResponse from(HelpRequest request, List<HelpDemoLink> demos) {
        HelpRequestResponse dto = new HelpRequestResponse();
        dto.id = request.getId();
        dto.status = request.getStatus();
        dto.equipmentId = request.getEquipment().getId();
        dto.equipmentCode = request.getEquipment().getCode();
        dto.equipmentName = request.getEquipment().getName();
        dto.exerciseName = request.getExercise() != null ? request.getExercise().getName() : null;
        dto.createdAt = request.getCreatedAt();
        dto.updatedAt = request.getUpdatedAt();
        dto.firstResponseAt = request.getFirstResponseAt();
        dto.closedAt = request.getClosedAt();
        dto.closedBy = request.getClosedBy();
        dto.demos = demos;
        return dto;
    }

    public Long getId() { return id; }
    public HelpRequestStatus getStatus() { return status; }
    public Long getEquipmentId() { return equipmentId; }
    public String getEquipmentCode() { return equipmentCode; }
    public String getEquipmentName() { return equipmentName; }
    public String getExerciseName() { return exerciseName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getFirstResponseAt() { return firstResponseAt; }
    public Instant getClosedAt() { return closedAt; }
    public HelpRequestClosedBy getClosedBy() { return closedBy; }
    public List<HelpDemoLink> getDemos() { return demos; }
}
