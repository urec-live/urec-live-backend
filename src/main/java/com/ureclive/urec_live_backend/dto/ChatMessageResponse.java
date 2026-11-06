package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.ChatMessageBase;
import com.ureclive.urec_live_backend.entity.DirectMessage;
import com.ureclive.urec_live_backend.entity.MessageKind;
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
    /** "TEXT", "GIF", "STICKER" or "IMAGE". */
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
        /**
         * IMAGE only: the photo's id. Its URL expires and changes on every fetch, so the app caches
         * the image by this id instead.
         */
        public Long mediaId;

        /** A GIF or sticker; null when there's no URL. */
        public static Media from(MessageMedia m) {
            if (m == null || m.getUrl() == null) return null;
            Media media = new Media();
            media.url = m.getUrl();
            media.previewUrl = m.getPreviewUrl();
            media.width = m.getWidth();
            media.height = m.getHeight();
            return media;
        }

        /** An uploaded photo, viewed through the expiring URL made for this response; null without one. */
        public static Media photo(MessageMedia m, String url) {
            if (m == null || m.getUploadId() == null || url == null) return null;
            Media media = new Media();
            media.url = url;
            media.width = m.getWidth();
            media.height = m.getHeight();
            media.mediaId = m.getUploadId();
            return media;
        }

        /** Either kind: photos need their URL from {@code photoUrl}. */
        public static Media of(ChatMessageBase message, String photoUrl) {
            return message.getKind() == MessageKind.IMAGE
                    ? photo(message.getMedia(), photoUrl)
                    : from(message.getMedia());
        }
    }

    public static ChatMessageResponse from(ChatMessageBase m) {
        return from(m, Map.of(), null);
    }

    public static ChatMessageResponse from(ChatMessageBase m, Map<String, List<String>> reactions) {
        return from(m, reactions, null);
    }

    /**
     * @param photoUrl for IMAGE messages, the expiring URL made for whoever gets this response; without
     *                 one the photo has no media (the app shows it as unavailable)
     */
    public static ChatMessageResponse from(ChatMessageBase m, Map<String, List<String>> reactions, String photoUrl) {
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
            r.media = Media.of(m, photoUrl);
            r.reactions.putAll(reactions);
        }
        return r;
    }
}
