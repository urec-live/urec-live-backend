package com.ureclive.urec_live_backend.entity;

/** What a chat message carries. Stored through {@link MessageKindConverter}, so new kinds can be added. */
public enum MessageKind {
    TEXT,
    /** An animated GIF picked from KLIPY. */
    GIF,
    /** A KLIPY sticker; shown large, without a bubble. */
    STICKER;

    /** GIFs and stickers carry media and no text. */
    public boolean hasMedia() {
        return this != TEXT;
    }
}
