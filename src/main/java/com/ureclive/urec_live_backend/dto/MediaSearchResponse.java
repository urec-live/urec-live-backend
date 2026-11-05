package com.ureclive.urec_live_backend.dto;

import java.util.List;

/** One page of GIFs or stickers for the app's picker, normalized from KLIPY's response. */
public class MediaSearchResponse {
    public List<Item> items;
    /** The page to ask for next, or null on the last page. */
    public Integer nextPage;

    public MediaSearchResponse() {}

    public MediaSearchResponse(List<Item> items, Integer nextPage) {
        this.items = items;
        this.nextPage = nextPage;
    }

    public static class Item {
        /** KLIPY's slug; sent back as media.providerId. */
        public String id;
        /** The rendition to send and show in chat. */
        public String url;
        /** A smaller rendition for the picker grid. */
        public String previewUrl;
        public Integer width;
        public Integer height;

        public Item() {}

        public Item(String id, String url, String previewUrl, Integer width, Integer height) {
            this.id = id;
            this.url = url;
            this.previewUrl = previewUrl;
            this.width = width;
            this.height = height;
        }
    }
}
