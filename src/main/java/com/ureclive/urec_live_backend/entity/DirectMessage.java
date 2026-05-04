package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "direct_messages", indexes = {
    @Index(name = "idx_dm_sender_recipient", columnList = "sender_username, recipient_username"),
    @Index(name = "idx_dm_recipient_sender", columnList = "recipient_username, sender_username")
})
public class DirectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_username", nullable = false, length = 50)
    private String senderUsername;

    @Column(name = "recipient_username", nullable = false, length = 50)
    private String recipientUsername;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(nullable = false)
    private Boolean read = false;

    @PrePersist
    void onSend() { this.sentAt = Instant.now(); }

    public Long getId() { return id; }
    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getSentAt() { return sentAt; }
    public Boolean getRead() { return read; }
    public void setRead(Boolean read) { this.read = read; }
}
