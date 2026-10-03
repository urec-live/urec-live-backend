package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.MachineDTO;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentStatuses;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineStatusServiceTest {

    @Mock private EquipmentRepository equipmentRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ActivityLogService activityLogService;

    @InjectMocks private MachineStatusService service;

    private Equipment legPress;

    @BeforeEach
    void setUp() {
        legPress = new Equipment("LP01", "Leg Press", EquipmentStatuses.AVAILABLE, null);
        legPress.setId(10L);
    }

    @Test
    void takingAMachineOutOfServiceSavesBroadcastsAndLogs() {
        when(equipmentRepository.save(legPress)).thenReturn(legPress);

        Equipment saved = service.changeStatus(legPress, EquipmentStatuses.OUT_OF_ORDER, "admin", null);

        assertEquals(EquipmentStatuses.OUT_OF_ORDER, saved.getStatus());
        MachineDTO broadcast = broadcastPayload();
        assertEquals(10L, broadcast.getId());
        assertEquals(EquipmentStatuses.OUT_OF_ORDER, broadcast.getStatus());
        verify(activityLogService).log("EQUIPMENT_OUT_OF_ORDER", "admin", "Leg Press marked out of order", "Leg Press");
    }

    @Test
    void bringingAMachineBackLogsTheReason() {
        legPress.setStatus(EquipmentStatuses.OUT_OF_ORDER);
        when(equipmentRepository.save(legPress)).thenReturn(legPress);

        service.changeStatus(legPress, EquipmentStatuses.AVAILABLE, "admin", "last open report resolved");

        assertEquals(EquipmentStatuses.AVAILABLE, broadcastPayload().getStatus());
        verify(activityLogService).log("EQUIPMENT_BACK_IN_SERVICE", "admin",
                "Leg Press back in service (last open report resolved)", "Leg Press");
    }

    @Test
    void otherStatusChangesAreBroadcastButNotLogged() {
        when(equipmentRepository.save(legPress)).thenReturn(legPress);

        service.changeStatus(legPress, "Reserved", "admin", null);

        assertEquals("Reserved", broadcastPayload().getStatus());
        verifyNoInteractions(activityLogService);
    }

    @Test
    void anUnchangedStatusDoesNothing() {
        Equipment result = service.changeStatus(legPress, EquipmentStatuses.AVAILABLE, "admin", null);

        assertSame(legPress, result);
        verifyNoInteractions(equipmentRepository, messagingTemplate, activityLogService);
    }

    private MachineDTO broadcastPayload() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/machines"), payload.capture());
        return (MachineDTO) payload.getValue();
    }
}
