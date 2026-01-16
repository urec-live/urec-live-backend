package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "equipment_events",
    indexes = {
        @Index(
            name = "idx_equipment_events_equipment_time",
            columnList = "equipment_id,occurred_at"
        ),
        @Index(
            name = "idx_equipment_events_session_time",
            columnList = "session_id,occurred_at"
        )
    }
)
public class EquipmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EquipmentEventType eventType;

    // ✅ Style A: relationships (still stored in the SAME columns)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private EquipmentSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "metadata")
    private String metadata;

    public EquipmentEvent() {}

    // ---- getters & setters ----

    public Long getId() {
        return id;
    }

    public EquipmentEventType getEventType() {
        return eventType;
    }

    public void setEventType(EquipmentEventType eventType) {
        this.eventType = eventType;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public void setEquipment(Equipment equipment) {
        this.equipment = equipment;
    }

    public EquipmentSession getSession() {
        return session;
    }

    public void setSession(EquipmentSession session) {
        this.session = session;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
