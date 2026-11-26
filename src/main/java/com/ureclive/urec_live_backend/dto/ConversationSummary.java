package com.ureclive.urec_live_backend.dto;

public class ConversationSummary {
    public String partnerUsername;
    /** Inbox preview: the text, or "GIF", "Sticker", "Photo", "Message deleted" or "Message removed". */
    public String lastMessage;
    public String lastSentAt;
    /** The last message's kind ("TEXT", "GIF", "STICKER", "IMAGE"). */
    public String lastKind;
    public boolean unread;
}
