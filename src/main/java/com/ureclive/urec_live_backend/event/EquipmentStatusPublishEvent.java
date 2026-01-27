package com.ureclive.urec_live_backend.event;

import com.ureclive.urec_live_backend.dto.EquipmentStatusUpdate;

public class EquipmentStatusPublishEvent {
    private final EquipmentStatusUpdate payload;

    public EquipmentStatusPublishEvent(EquipmentStatusUpdate payload) {
        this.payload = payload;
    }

    public EquipmentStatusUpdate getPayload() {
        return payload;
    }
}
