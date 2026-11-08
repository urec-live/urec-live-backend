package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Columns shared by community and direct messages. New columns are nullable or have a database
 * default, so ddl-auto=update can add them to tables that already hold messages.
 */
@MappedSuperclass
public abstract class ChatMessageBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_username", nullable = false, length = 50)
    private String senderUsername;

    /** Empty for GIFs and stickers. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Convert(converter = MessageKindConverter.class)
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10) default 'TEXT'")
    private MessageKind kind = MessageKind.TEXT;

    @Embedded
    private MessageMedia media;

    /** The id the sending app gave the message, so a retried send isn't saved twice. */
    @Column(name = "client_id", length = 36)
    private String clientId;

    /** Set when the sender deleted the message. The row stays so staff can still review reports. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "removed_by_staff", nullable = false, columnDefinition = "boolean default false")
    private boolean removedByStaff = false;

    @PrePersist
    void onSend() { this.sentAt = Instant.now(); }

    public abstract ChatChannel getChannel();

    /** Hidden from members, whether the sender deleted it or staff removed it. */
    public boolean isHidden() { return deletedAt != null || removedByStaff; }

    public Long getId() { return id; }
    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getSentAt() { return sentAt; }
    public MessageKind getKind() { return kind == null ? MessageKind.TEXT : kind; }
    public void setKind(MessageKind kind) { this.kind = kind; }
    public MessageMedia getMedia() { return media; }
    public void setMedia(MessageMedia media) { this.media = media; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public boolean isRemovedByStaff() { return removedByStaff; }
    public void setRemovedByStaff(boolean removedByStaff) { this.removedByStaff = removedByStaff; }
}
