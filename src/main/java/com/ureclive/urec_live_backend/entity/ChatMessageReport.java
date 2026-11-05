package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A member's report of a community or direct message, reviewed by staff in the admin dashboard.
 * A member can report a given message once.
 */
@Entity
@Table(name = "chat_message_reports",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_report_message_reporter", columnNames = {"channel", "message_id", "reporter_username"}),
    indexes = {
        @Index(name = "idx_chat_report_message", columnList = "channel, message_id"),
        @Index(name = "idx_chat_report_status", columnList = "status")
    })
public class ChatMessageReport {

    public static final int MAX_NOTE_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = ChatChannelConverter.class)
    @Column(nullable = false, length = 10)
    private ChatChannel channel;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "reporter_username", nullable = false, length = 50)
    private String reporterUsername;

    @Convert(converter = ReportEnumConverters.ReasonConverter.class)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(length = MAX_NOTE_LENGTH)
    private String note;

    @Convert(converter = ReportEnumConverters.StatusConverter.class)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** The staff member who dismissed the report or removed the message. */
    @Column(name = "resolved_by", length = 50)
    private String resolvedBy;

    /**
     * Optimistic lock, so two staff acting on the same message at once can't both succeed (one
     * dismissing, one removing). The default fills any rows saved before the column existed.
     */
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    @PrePersist
    void onCreate() {
        if (reportedAt == null) reportedAt = Instant.now();
    }

    public ChatMessageReport() {}

    public ChatMessageReport(ChatChannel channel, Long messageId, String reporterUsername,
                             ReportReason reason, String note) {
        this.channel = channel;
        this.messageId = messageId;
        this.reporterUsername = reporterUsername;
        this.reason = reason;
        this.note = note;
    }

    /** Closes an open report. Returns false (and changes nothing) if it's already closed. */
    public boolean resolve(ReportStatus outcome, String staffUsername) {
        if (status != ReportStatus.OPEN || outcome == ReportStatus.OPEN) return false;
        status = outcome;
        resolvedAt = Instant.now();
        resolvedBy = staffUsername;
        return true;
    }

    public Long getId() { return id; }
    public ChatChannel getChannel() { return channel; }
    public Long getMessageId() { return messageId; }
    public String getReporterUsername() { return reporterUsername; }
    public ReportReason getReason() { return reason; }
    public String getNote() { return note; }
    public ReportStatus getStatus() { return status; }
    public Instant getReportedAt() { return reportedAt; }
    public void setReportedAt(Instant reportedAt) { this.reportedAt = reportedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
}
