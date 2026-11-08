package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;

/** The unique (sender, clientId) pair stops a retried send being saved twice; rows without a clientId don't clash. */
@Entity
@Table(name = "community_messages", uniqueConstraints = {
    @UniqueConstraint(name = "uk_cm_sender_client", columnNames = {"sender_username", "client_id"})
})
public class CommunityMessage extends ChatMessageBase {

    @Override
    public ChatChannel getChannel() { return ChatChannel.COMMUNITY; }
}
