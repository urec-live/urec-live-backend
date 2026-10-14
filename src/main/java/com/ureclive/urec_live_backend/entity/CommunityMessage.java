package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "community_messages")
public class CommunityMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_username", nullable = false, length = 50)
    private String senderUsername;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @PrePersist
    void onSend() { this.sentAt = Instant.now(); }

    public Long getId() { return id; }
    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getSentAt() { return sentAt; }
}
