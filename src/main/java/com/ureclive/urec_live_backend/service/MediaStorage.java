package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.ChatMedia;

import java.time.Duration;

/**
 * Where chat photos are kept: Cloudflare R2 ({@link S3MediaStorage}) in production, or a folder on
 * this machine ({@link LocalDiskMediaStorage}) for development and tests. Chosen by media.storage;
 * see {@link MediaStorageConfig}.
 */
public interface MediaStorage {

    /** Saves the bytes under the key, replacing anything already there. */
    void put(String key, byte[] bytes, String contentType);

    /**
     * A URL that lets whoever holds it GET the photo until {@code ttl} passes, with no sign-in. Only
     * handed to people allowed to see the message. May be relative to the backend ("/api/…").
     */
    String viewUrl(ChatMedia media, Duration ttl);
}
