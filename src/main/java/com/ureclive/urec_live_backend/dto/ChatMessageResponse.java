package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.CommunityMessage;
import com.ureclive.urec_live_backend.entity.DirectMessage;

import java.time.Instant;

public class ChatMessageResponse {
    public Long id;
    public String senderUsername;
    public String content;
    public String sentAt;
    public String type;
    public String recipientUsername;

    public static ChatMessageResponse from(CommunityMessage m) {
        ChatMessageResponse r = new ChatMessageResponse();
        r.id = m.getId();
        r.senderUsername = m.getSenderUsername();
        r.content = m.getContent();
        r.sentAt = m.getSentAt().toString();
        r.type = "COMMUNITY";
        return r;
    }

    public static ChatMessageResponse from(DirectMessage m) {
        ChatMessageResponse r = new ChatMessageResponse();
        r.id = m.getId();
        r.senderUsername = m.getSenderUsername();
        r.recipientUsername = m.getRecipientUsername();
        r.content = m.getContent();
        r.sentAt = m.getSentAt().toString();
        r.type = "DM";
        return r;
    }
}
