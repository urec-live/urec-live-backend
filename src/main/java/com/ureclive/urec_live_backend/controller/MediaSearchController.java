package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.MediaSearchResponse;
import com.ureclive.urec_live_backend.service.KlipyClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * The app's GIF and sticker picker. Proxies KLIPY so the API key never ships in the app; signed-in
 * users only. Answers 503 until KLIPY_API_KEY is configured.
 */
@RestController
@RequestMapping("/api/media")
public class MediaSearchController {

    private final KlipyClient klipy;

    @Autowired
    public MediaSearchController(KlipyClient klipy) {
        this.klipy = klipy;
    }

    /** GET /api/media/{gifs|stickers}/trending?page=1 */
    @GetMapping("/{category}/trending")
    public MediaSearchResponse trending(
            @PathVariable String category,
            @RequestParam(defaultValue = "1") int page,
            Authentication auth) {
        return klipy.trending(KlipyClient.Category.fromPath(category), page, auth.getName());
    }

    /** GET /api/media/{gifs|stickers}/search?q=deadlift&page=1 */
    @GetMapping("/{category}/search")
    public MediaSearchResponse search(
            @PathVariable String category,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page,
            Authentication auth) {
        return klipy.search(KlipyClient.Category.fromPath(category), q, page, auth.getName());
    }
}
