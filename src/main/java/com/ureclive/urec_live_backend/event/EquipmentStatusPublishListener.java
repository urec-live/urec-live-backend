package com.ureclive.urec_live_backend.event;

import com.ureclive.urec_live_backend.dto.EquipmentStatusUpdate;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EquipmentStatusPublishListener {

    private static final Logger logger = LoggerFactory.getLogger(EquipmentStatusPublishListener.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final MeterRegistry meterRegistry;

    public EquipmentStatusPublishListener(
            SimpMessagingTemplate messagingTemplate,
            MeterRegistry meterRegistry
    ) {
        this.messagingTemplate = messagingTemplate;
        this.meterRegistry = meterRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublish(EquipmentStatusPublishEvent event) {
        EquipmentStatusUpdate payload = event.getPayload();
        try {
            logger.info("[WS] publishing equipment-status: {} -> {}", payload.getEquipmentId(), payload.getStatus());
            messagingTemplate.convertAndSend("/topic/equipment/" + payload.getEquipmentId(), payload);
            messagingTemplate.convertAndSend("/topic/equipment-status", payload);
            meterRegistry.counter("urec.ws.publish", "result", "success").increment();
        } catch (Exception ex) {
            meterRegistry.counter("urec.ws.publish", "result", "failure").increment();
            logger.error("[WS] failed to publish equipment-status: {} -> {}", payload.getEquipmentId(), payload.getStatus(), ex);
            throw ex;
        }
    }
}
