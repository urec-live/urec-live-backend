package com.ureclive.urec_live_backend.entity;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The two chat rooms: the global community chat and one-to-one direct messages. */
public enum ChatChannel {
    COMMUNITY("community"),
    DM("dm");

    private final String path;

    ChatChannel(String path) {
        this.path = path;
    }

    /** The URL segment, as in /api/messaging/{channel}/messages/{id}. */
    public String path() {
        return path;
    }

    /** Resolves a URL segment; anything else is a 404, like an unknown route. */
    public static ChatChannel fromPath(String path) {
        for (ChatChannel channel : values()) {
            if (channel.path.equalsIgnoreCase(path)) return channel;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown chat channel: " + path);
    }
}
