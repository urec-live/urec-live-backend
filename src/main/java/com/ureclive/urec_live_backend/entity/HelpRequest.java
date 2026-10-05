package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A member's "call staff" request at a machine. Staff respond with "On the way" or "Too busy" and
 * close it with "Done helping"; the member can close it with "Received help" or cancel it.
 */
@Entity
@Table(name = "help_requests", indexes = {
    @Index(name = "idx_help_requests_status", columnList = "status"),
    @Index(name = "idx_help_requests_member", columnList = "member_id")
})
public class HelpRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private User member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    /** The exercise the member is attempting, when the app knows it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HelpRequestStatus status = HelpRequestStatus.REQUEST_RECEIVED;

    /** The staff member who last responded to (or closed) the request. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_member_id")
    private User staffMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "closed_by", length = 10)
    private HelpRequestClosedBy closedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Optimistic lock, so a staff response can't reopen a request the member just closed. */
    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public HelpRequest() {}

    public HelpRequest(User member, Equipment equipment, Exercise exercise) {
        this.member = member;
        this.equipment = equipment;
        this.exercise = exercise;
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    /**
     * Records a staff response ("On the way" or "Too busy") from {@code staff}.
     * Returns false (and changes nothing) if the request already has that status.
     */
    public boolean respond(User staff, HelpRequestStatus newStatus, Instant now) {
        if (!newStatus.isStaffResponse()) {
            throw new IllegalArgumentException("Not a staff response: " + newStatus);
        }
        requireOpen();
        if (newStatus == status) return false;
        status = newStatus;
        staffMember = staff;
        if (firstResponseAt == null) firstResponseAt = now;
        updatedAt = now;
        return true;
    }

    /** Closes the request for good. {@code staff} is the staff member who closed it, if any. */
    public void close(HelpRequestStatus finalStatus, HelpRequestClosedBy by, User staff, Instant now) {
        if (finalStatus.isOpen()) {
            throw new IllegalArgumentException("Not a closing status: " + finalStatus);
        }
        requireOpen();
        status = finalStatus;
        closedBy = by;
        closedAt = now;
        updatedAt = now;
        if (staff != null) {
            staffMember = staff;
            if (firstResponseAt == null) firstResponseAt = now;
        }
    }

    private void requireOpen() {
        if (!isOpen()) throw new IllegalStateException("Help request " + id + " is already " + status);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getMember() { return member; }
    public void setMember(User member) { this.member = member; }

    public Equipment getEquipment() { return equipment; }
    public void setEquipment(Equipment equipment) { this.equipment = equipment; }

    public Exercise getExercise() { return exercise; }
    public void setExercise(Exercise exercise) { this.exercise = exercise; }

    public HelpRequestStatus getStatus() { return status; }
    public void setStatus(HelpRequestStatus status) { this.status = status; }

    public User getStaffMember() { return staffMember; }
    public void setStaffMember(User staffMember) { this.staffMember = staffMember; }

    public HelpRequestClosedBy getClosedBy() { return closedBy; }
    public void setClosedBy(HelpRequestClosedBy closedBy) { this.closedBy = closedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getFirstResponseAt() { return firstResponseAt; }
    public void setFirstResponseAt(Instant firstResponseAt) { this.firstResponseAt = firstResponseAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
