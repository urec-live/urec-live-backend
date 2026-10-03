package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.MachineDTO;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentStatuses;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** Status changes made by staff, as opposed to members checking in and out. */
@Service
public class MachineStatusService {

    private final EquipmentRepository equipmentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ActivityLogService activityLogService;

    @Autowired
    public MachineStatusService(EquipmentRepository equipmentRepository,
                                SimpMessagingTemplate messagingTemplate,
                                ActivityLogService activityLogService) {
        this.equipmentRepository = equipmentRepository;
        this.messagingTemplate = messagingTemplate;
        this.activityLogService = activityLogService;
    }

    /**
     * Saves the new status, pushes the machine to /topic/machines so open app and admin screens update
     * live, and logs EQUIPMENT_OUT_OF_ORDER / EQUIPMENT_BACK_IN_SERVICE when the machine goes out of or
     * comes back into service. Does nothing if the status is unchanged.
     *
     * @param reason optional note for the activity log, e.g. "last open report resolved"
     */
    public Equipment changeStatus(Equipment equipment, String newStatus, String actor, String reason) {
        String oldStatus = equipment.getStatus();
        if (newStatus.equals(oldStatus)) return equipment;

        equipment.setStatus(newStatus);
        Equipment saved = equipmentRepository.save(equipment);
        messagingTemplate.convertAndSend("/topic/machines", new MachineDTO(saved, primaryExerciseName(saved)));

        boolean wasOutOfOrder = EquipmentStatuses.isOutOfOrder(oldStatus);
        boolean isOutOfOrder = EquipmentStatuses.isOutOfOrder(newStatus);
        String note = reason != null ? " (" + reason + ")" : "";
        if (!wasOutOfOrder && isOutOfOrder) {
            activityLogService.log("EQUIPMENT_OUT_OF_ORDER", actor,
                    saved.getName() + " marked out of order" + note, saved.getName());
        } else if (wasOutOfOrder && !isOutOfOrder) {
            activityLogService.log("EQUIPMENT_BACK_IN_SERVICE", actor,
                    saved.getName() + " back in service" + note, saved.getName());
        }
        return saved;
    }

    // Same rule as MachineController.getPrimaryExerciseName
    private String primaryExerciseName(Equipment equipment) {
        return equipment.getExercises().stream()
                .findFirst()
                .map(Exercise::getName)
                .orElse("Unknown");
    }
}
