package com.ureclive.urec_live_backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A rejected chat send. The STOMP controller turns it into a ChatErrorResponse on the sender's error queue. */
public class ChatSendException extends ResponseStatusException {

    private final String code;

    public ChatSendException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
