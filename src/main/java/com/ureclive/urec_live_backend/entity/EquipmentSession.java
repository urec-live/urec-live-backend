package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "equipment_sessions", indexes = {
        @Index(name = "idx_equipment_sessions_equipment_status", columnList = "equipment_id,status"),
        @Index(name = "idx_equipment_sessions_user_started", columnList = "user_id,started_at"),
        @Index(name = "idx_equipment_sessions_status_started", columnList = "status,started_at"),
        @Index(name = "idx_equipment_sessions_equipment_ended", columnList = "equipment_id,ended_at,status"),
        @Index(name = "idx_equipment_sessions_status_heartbeat", columnList = "status,last_heartbeat_at"),
        @Index(name = "idx_equipment_sessions_status_warning", columnList = "status,last_timeout_warning_at")
})
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

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "last_timeout_warning_at")
    private Instant lastTimeoutWarningAt;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private GymLocation location;

    public EquipmentSession() {
    }

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

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public Instant getLastTimeoutWarningAt() {
        return lastTimeoutWarningAt;
    }

    public void setLastTimeoutWarningAt(Instant lastTimeoutWarningAt) {
        this.lastTimeoutWarningAt = lastTimeoutWarningAt;
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

    public GymLocation getLocation() {
        return location;
    }

    public void setLocation(GymLocation location) {
        this.location = location;
    }
}
