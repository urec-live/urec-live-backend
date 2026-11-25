package com.ureclive.urec_live_backend.entity;

/** What a chat message carries. Stored through {@link MessageKindConverter}, so new kinds can be added. */
public enum MessageKind {
    TEXT,
    /** An animated GIF picked from KLIPY. */
    GIF,
    /** A KLIPY sticker; shown large, without a bubble. */
    STICKER,
    /** A photo the sender uploaded (ChatMedia), with an optional caption in the content. */
    IMAGE;

    /** Everything but TEXT carries media. */
    public boolean hasMedia() {
        return this != TEXT;
    }

    /** GIFs and stickers: media from KLIPY, and no text. */
    public boolean isKlipy() {
        return this == GIF || this == STICKER;
    }
}
