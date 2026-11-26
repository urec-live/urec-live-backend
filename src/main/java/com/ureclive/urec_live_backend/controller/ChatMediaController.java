package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.ChatMediaUploadResponse;
import com.ureclive.urec_live_backend.entity.ChatMedia;
import com.ureclive.urec_live_backend.repository.ChatMediaRepository;
import com.ureclive.urec_live_backend.service.ChatMediaService;
import com.ureclive.urec_live_backend.service.LocalDiskMediaStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Chat photo uploads. A photo is uploaded first, then sent as an IMAGE message carrying the returned
 * mediaId over STOMP like any other message.
 */
@RestController
@RequestMapping("/api/messaging/media")
public class ChatMediaController {

    private final ChatMediaService media;
    private final ChatMediaRepository mediaRepo;

    @Autowired
    public ChatMediaController(ChatMediaService media, ChatMediaRepository mediaRepo) {
        this.media = media;
        this.mediaRepo = mediaRepo;
    }

    /**
     * POST /api/messaging/media (multipart "file") — a JPEG of at most 5 MB. 201 with {mediaId,
     * width, height}; 413 too big, 415 not a JPEG, 400 unreadable, 429 too many unsent uploads.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatMediaUploadResponse> upload(
            @RequestParam(value = "file", required = false) MultipartFile file, Authentication auth) {
        ChatMedia saved = media.upload(auth.getName(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ChatMediaUploadResponse.from(saved));
    }

    /**
     * GET /api/messaging/media/{id}/raw?exp=…&sig=… — the photo itself, when media.storage=local. Open
     * to anyone holding an unexpired signed URL, like a presigned R2 URL; 403 otherwise.
     */
    @GetMapping("/{id}/raw")
    public ResponseEntity<byte[]> raw(
            @PathVariable long id,
            @RequestParam(required = false) Long exp,
            @RequestParam(required = false) String sig) {
        if (!(media.storage() instanceof LocalDiskMediaStorage local)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (exp == null || !local.isValid(id, exp, sig)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This photo link is invalid or has expired");
        }
        ChatMedia photo = mediaRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        byte[] bytes = local.read(photo.getStorageKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(local.secondsUntil(exp))).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }
}
