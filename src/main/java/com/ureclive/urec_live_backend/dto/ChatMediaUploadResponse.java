package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.ChatMedia;

/** A stored chat photo, ready to send: put mediaId in an IMAGE message's media block. */
public class ChatMediaUploadResponse {
    public Long mediaId;
    /** Size after re-encoding, upright. */
    public int width;
    public int height;

    public static ChatMediaUploadResponse from(ChatMedia media) {
        ChatMediaUploadResponse r = new ChatMediaUploadResponse();
        r.mediaId = media.getId();
        r.width = media.getWidth();
        r.height = media.getHeight();
        return r;
    }
}
