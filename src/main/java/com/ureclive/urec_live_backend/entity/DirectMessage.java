package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;

/** The unique (sender, clientId) pair stops a retried send being saved twice; rows without a clientId don't clash. */
@Entity
@Table(name = "direct_messages",
    uniqueConstraints = @UniqueConstraint(name = "uk_dm_sender_client", columnNames = {"sender_username", "client_id"}),
    indexes = {
        @Index(name = "idx_dm_sender_recipient", columnList = "sender_username, recipient_username"),
        @Index(name = "idx_dm_recipient_sender", columnList = "recipient_username, sender_username")
    })
public class DirectMessage extends ChatMessageBase {

    @Column(name = "recipient_username", nullable = false, length = 50)
    private String recipientUsername;

    @Column(nullable = false)
    private Boolean read = false;

    @Override
    public ChatChannel getChannel() { return ChatChannel.DM; }

    /** True if the user sent or received this message. */
    public boolean involves(String username) {
        return getSenderUsername().equals(username) || recipientUsername.equals(username);
    }

    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }
    public Boolean getRead() { return read; }
    public void setRead(Boolean read) { this.read = read; }
}
