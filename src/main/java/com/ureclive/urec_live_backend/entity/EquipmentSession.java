package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "equipment_sessions",
    indexes = {
        @Index(
            name = "idx_equipment_sessions_equipment_status",
            columnList = "equipment_id,status"
        ),
        @Index(
            name = "idx_equipment_sessions_user_started",
            columnList = "user_id,started_at"
        )
    }
)
public class EquipmentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EquipmentSessionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private EquipmentSessionEndReason endReason;

    // ✅ relationships (still stored in SAME columns: equipment_id, user_id)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public EquipmentSession() {}

    // ---- getters & setters ----

    public Long getId() {
        return id;
    }

    public EquipmentSessionStatus getStatus() {
        return status;
    }

    public void setStatus(EquipmentSessionStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public EquipmentSessionEndReason getEndReason() {
        return endReason;
    }

    public void setEndReason(EquipmentSessionEndReason endReason) {
        this.endReason = endReason;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public void setEquipment(Equipment equipment) {
        this.equipment = equipment;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
