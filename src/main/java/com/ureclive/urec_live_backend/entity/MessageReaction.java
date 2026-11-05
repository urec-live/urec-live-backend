package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;

/**
 * One member's emoji reaction to a chat message. A member has at most one reaction per message;
 * picking another emoji replaces it. Messages live in two tables, so the message is referenced by
 * channel + id rather than a foreign key.
 */
@Entity
@Table(name = "chat_message_reactions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_reaction_message_user", columnNames = {"channel", "message_id", "username"}),
    indexes = @Index(name = "idx_reaction_message", columnList = "channel, message_id"))
public class MessageReaction {

    /** The quick-reaction bar, in display order. Only these can be stored. */
    public static final List<String> ALLOWED_EMOJI = List.of("👍", "❤️", "😂", "🔥", "💪", "😮");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = ChatChannelConverter.class)
    @Column(nullable = false, length = 10)
    private ChatChannel channel;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 16)
    private String emoji;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public MessageReaction() {}

    public MessageReaction(ChatChannel channel, Long messageId, String username, String emoji) {
        this.channel = channel;
        this.messageId = messageId;
        this.username = username;
        this.emoji = emoji;
    }

    /** The allowed emoji matching the input, or null. A bare ❤ (no variation selector) counts as ❤️. */
    public static String normalizeEmoji(String emoji) {
        if (emoji == null) return null;
        String trimmed = emoji.strip();
        if (trimmed.equals("❤")) trimmed = "❤️";
        return ALLOWED_EMOJI.contains(trimmed) ? trimmed : null;
    }

    public Long getId() { return id; }
    public ChatChannel getChannel() { return channel; }
    public Long getMessageId() { return messageId; }
    public String getUsername() { return username; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
