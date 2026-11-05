package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.ChatMessageBase;
import com.ureclive.urec_live_backend.entity.DirectMessage;
import com.ureclive.urec_live_backend.entity.MessageMedia;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A chat message as members see it. Sent in history pages and on every live change (new message,
 * reaction, delete, staff removal), so the app replaces what it has by id. Hidden messages keep
 * their place in the conversation but carry no content, media or reactions.
 */
public class ChatMessageResponse {
    public Long id;
    public String senderUsername;
    public String content;
    public String sentAt;
    /** The room: "COMMUNITY" or "DM". What the message holds is {@link #kind}. */
    public String type;
    public String recipientUsername;
    /** "TEXT", "GIF" or "STICKER". */
    public String kind;
    public Media media;
    /** Echoes the sender's clientId so the app can swap its pending bubble for this one. */
    public String clientId;
    /** The sender deleted it. */
    public boolean deleted;
    /** Staff removed it after a report. */
    public boolean removedByStaff;
    /** Emoji to the usernames who reacted with it, in the order the emoji were first used. */
    public Map<String, List<String>> reactions = new LinkedHashMap<>();

    public static class Media {
        public String url;
        public String previewUrl;
        public Integer width;
        public Integer height;

        public static Media from(MessageMedia m) {
            if (m == null || m.getUrl() == null) return null;
            Media media = new Media();
            media.url = m.getUrl();
            media.previewUrl = m.getPreviewUrl();
            media.width = m.getWidth();
            media.height = m.getHeight();
            return media;
        }
    }

    public static ChatMessageResponse from(ChatMessageBase m) {
        return from(m, Map.of());
    }

    public static ChatMessageResponse from(ChatMessageBase m, Map<String, List<String>> reactions) {
        ChatMessageResponse r = new ChatMessageResponse();
        r.id = m.getId();
        r.senderUsername = m.getSenderUsername();
        r.sentAt = m.getSentAt().toString();
        r.type = m.getChannel().name();
        if (m instanceof DirectMessage dm) r.recipientUsername = dm.getRecipientUsername();
        r.kind = m.getKind().name();
        r.clientId = m.getClientId();
        r.deleted = m.getDeletedAt() != null;
        r.removedByStaff = m.isRemovedByStaff();
        if (m.isHidden()) {
            r.content = "";
        } else {
            r.content = m.getContent();
            r.media = Media.from(m.getMedia());
            r.reactions.putAll(reactions);
        }
        return r;
    }
}
