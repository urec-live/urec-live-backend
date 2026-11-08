package com.ureclive.urec_live_backend.dto;

/** Sent on the sender's /user/queue/errors when a chat send is rejected, so the app can mark that bubble failed. */
public class ChatErrorResponse {
    public String clientId;
    /**
     * Machine-readable reason, e.g. TOO_LONG, EMPTY, UNKNOWN_RECIPIENT, INVALID_MEDIA, SEND_FAILED,
     * or INVALID_MESSAGE when the JSON itself couldn't be read (clientId may then be null).
     */
    public String code;
    public String message;

    public ChatErrorResponse() {}

    public ChatErrorResponse(String clientId, String code, String message) {
        this.clientId = clientId;
        this.code = code;
        this.message = message;
    }
}
