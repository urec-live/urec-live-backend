package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A photo a member uploaded for chat. It's stored (re-encoded, without EXIF) under {@link #storageKey}
 * in the configured MediaStorage, and goes on at most one message: sending marks it attached in the
 * same transaction that saves the message. The row and file stay after the message is deleted, so
 * staff can still review reports on it.
 */
@Entity
@Table(name = "chat_media")
public class ChatMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploader_username", nullable = false, length = 50)
    private String uploaderUsername;

    /** Where the file lives in MediaStorage, e.g. "chat-media/9f1c….jpg". Never shown to members. */
    @Column(name = "storage_key", nullable = false, unique = true, length = 200)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    /** Bytes as stored, after re-encoding. */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    /** True once a message carries it; a photo can't be sent twice. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean attached = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMedia() {}

    public ChatMedia(String uploaderUsername, String storageKey, String contentType, long sizeBytes, int width, int height) {
        this.uploaderUsername = uploaderUsername;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getUploaderUsername() { return uploaderUsername; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isAttached() { return attached; }
    public Instant getCreatedAt() { return createdAt; }
}
