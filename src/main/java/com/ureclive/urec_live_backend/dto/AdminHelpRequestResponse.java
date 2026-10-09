package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.HelpRequest;
import com.ureclive.urec_live_backend.entity.HelpRequestClosedBy;
import com.ureclive.urec_live_backend.entity.HelpRequestStatus;

import java.time.Instant;

/** The staff view of a help request: who needs help, where, and who is handling it. */
public class AdminHelpRequestResponse {

    private Long id;
    private HelpRequestStatus status;
    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    private String floorLabel;
    private String exerciseName;
    private String memberUsername;
    private String staffUsername;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant firstResponseAt;
    private Instant closedAt;
    private HelpRequestClosedBy closedBy;

    public AdminHelpRequestResponse() {}

    public static AdminHelpRequestResponse from(HelpRequest request) {
        AdminHelpRequestResponse dto = new AdminHelpRequestResponse();
        dto.id = request.getId();
        dto.status = request.getStatus();
        dto.equipmentId = request.getEquipment().getId();
        dto.equipmentCode = request.getEquipment().getCode();
        dto.equipmentName = request.getEquipment().getName();
        dto.floorLabel = request.getEquipment().getFloorLabel();
        dto.exerciseName = request.getExercise() != null ? request.getExercise().getName() : null;
        dto.memberUsername = request.getMember().getUsername();
        dto.staffUsername = request.getStaffMember() != null ? request.getStaffMember().getUsername() : null;
        dto.createdAt = request.getCreatedAt();
        dto.updatedAt = request.getUpdatedAt();
        dto.firstResponseAt = request.getFirstResponseAt();
        dto.closedAt = request.getClosedAt();
        dto.closedBy = request.getClosedBy();
        return dto;
    }

    public Long getId() { return id; }
    public HelpRequestStatus getStatus() { return status; }
    public Long getEquipmentId() { return equipmentId; }
    public String getEquipmentCode() { return equipmentCode; }
    public String getEquipmentName() { return equipmentName; }
    public String getFloorLabel() { return floorLabel; }
    public String getExerciseName() { return exerciseName; }
    public String getMemberUsername() { return memberUsername; }
    public String getStaffUsername() { return staffUsername; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getFirstResponseAt() { return firstResponseAt; }
    public Instant getClosedAt() { return closedAt; }
    public HelpRequestClosedBy getClosedBy() { return closedBy; }
}
