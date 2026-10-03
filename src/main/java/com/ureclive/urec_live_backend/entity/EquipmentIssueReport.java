package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "equipment_issue_reports", indexes = {
    @Index(name = "idx_issue_reports_equipment", columnList = "equipment_id"),
    @Index(name = "idx_issue_reports_status", columnList = "status")
})
public class EquipmentIssueReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueSeverity severity;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueStatus status = IssueStatus.REPORTED;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (reportedAt == null) reportedAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public EquipmentIssueReport() {}

    public EquipmentIssueReport(Equipment equipment, User reporter, IssueSeverity severity, String description) {
        this.equipment = equipment;
        this.reporter = reporter;
        this.severity = severity;
        this.description = description;
    }

    /**
     * Moves the report to {@code newStatus}, keeping updatedAt and resolvedAt in step.
     * Returns false (and changes nothing) if the report already has that status.
     */
    public boolean changeStatus(IssueStatus newStatus) {
        if (newStatus == status) return false;
        Instant now = Instant.now();
        resolvedAt = newStatus == IssueStatus.RESOLVED ? now : null;
        status = newStatus;
        updatedAt = now;
        return true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Equipment getEquipment() { return equipment; }
    public void setEquipment(Equipment equipment) { this.equipment = equipment; }

    public User getReporter() { return reporter; }
    public void setReporter(User reporter) { this.reporter = reporter; }

    public IssueSeverity getSeverity() { return severity; }
    public void setSeverity(IssueSeverity severity) { this.severity = severity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public IssueStatus getStatus() { return status; }
    public void setStatus(IssueStatus status) { this.status = status; }

    public Instant getReportedAt() { return reportedAt; }
    public void setReportedAt(Instant reportedAt) { this.reportedAt = reportedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
