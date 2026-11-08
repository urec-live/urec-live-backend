package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The GIF or sticker attached to a chat message. Every column is nullable, so text messages (and
 * rows from before media existed) load with no media at all.
 */
@Embeddable
public class MessageMedia {

    @Column(name = "media_url", length = 1000)
    private String url;

    /** A smaller rendition for lists and previews. */
    @Column(name = "media_preview_url", length = 1000)
    private String previewUrl;

    @Column(name = "media_width")
    private Integer width;

    @Column(name = "media_height")
    private Integer height;

    /** The provider's id for the item (KLIPY slug), used for its share trigger. */
    @Column(name = "media_provider_id", length = 100)
    private String providerId;

    public MessageMedia() {}

    public MessageMedia(String url, String previewUrl, Integer width, Integer height, String providerId) {
        this.url = url;
        this.previewUrl = previewUrl;
        this.width = width;
        this.height = height;
        this.providerId = providerId;
    }

    public String getUrl() { return url; }
    public String getPreviewUrl() { return previewUrl; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public String getProviderId() { return providerId; }
}
