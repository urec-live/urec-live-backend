package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.ChatMedia;
import com.ureclive.urec_live_backend.repository.ChatMediaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Chat photos: checking and storing uploads, letting a send claim one, and making the expiring URLs
 * members and staff view them with. A URL is only made while building a response for someone who
 * may see the message, so the bucket itself stays private.
 */
@Service
public class ChatMediaService {

    private static final Logger logger = LoggerFactory.getLogger(ChatMediaService.class);

    public static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    public static final String CONTENT_TYPE = "image/jpeg";
    static final String KEY_PREFIX = "chat-media/";
    /** The window for the cap on photos uploaded but not sent yet. */
    static final Duration PENDING_WINDOW = Duration.ofHours(1);

    private final ChatMediaRepository repo;
    private final MediaStorage storage;
    private final ChatImageProcessor processor;
    private final Duration urlTtl;
    private final int maxPendingUploads;
    private final Clock clock;

    @Autowired
    public ChatMediaService(
            ChatMediaRepository repo,
            MediaStorage storage,
            ChatImageProcessor processor,
            @Value("${media.url-ttl-minutes:120}") long urlTtlMinutes,
            @Value("${media.max-pending-uploads:20}") int maxPendingUploads) {
        this(repo, storage, processor, Duration.ofMinutes(Math.max(1, urlTtlMinutes)), maxPendingUploads, Clock.systemUTC());
    }

    ChatMediaService(ChatMediaRepository repo, MediaStorage storage, ChatImageProcessor processor,
                     Duration urlTtl, int maxPendingUploads, Clock clock) {
        this.repo = repo;
        this.storage = storage;
        this.processor = processor;
        this.urlTtl = urlTtl;
        this.maxPendingUploads = maxPendingUploads;
        this.clock = clock;
    }

    public MediaStorage storage() {
        return storage;
    }

    // ── Uploading ──────────────────────────────────────────────────────────

    /**
     * Checks a photo (a JPEG of at most 5 MB), re-encodes it without metadata, stores it and returns
     * the record a send can then attach. Nothing is stored when a check fails.
     */
    public ChatMedia upload(String uploader, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a photo to send");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Photos can be at most 5 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Couldn't read the upload", e);
        }
        // The declared size can be missing or wrong, so check what actually arrived as well
        if (bytes.length > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Photos can be at most 5 MB");
        }
        if (!ChatImageProcessor.looksLikeJpeg(bytes)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Photos must be JPEG");
        }
        long pending = repo.countByUploaderUsernameAndAttachedFalseAndCreatedAtAfter(
                uploader, clock.instant().minus(PENDING_WINDOW));
        if (pending >= maxPendingUploads) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many photos uploaded without sending them. Try again later.");
        }

        ChatImageProcessor.ProcessedImage image;
        try {
            image = processor.process(bytes);
        } catch (ChatImageProcessor.InvalidImageException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Couldn't read that photo", e);
        }

        String key = KEY_PREFIX + UUID.randomUUID() + ".jpg";
        try {
            storage.put(key, image.jpeg(), CONTENT_TYPE);
        } catch (RuntimeException e) {
            logger.error("[Media] storing {} for {} failed", key, uploader, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Couldn't save the photo. Try again.", e);
        }
        return repo.save(new ChatMedia(uploader, key, CONTENT_TYPE, image.jpeg().length, image.width(), image.height()));
    }

    // ── Sending ────────────────────────────────────────────────────────────

    /**
     * The photo a send wants to attach, if the sender may: it must be theirs and not on a message yet.
     * The send then claims it with {@link #claim} as it saves the message.
     */
    public ChatMedia attachable(String sender, Long mediaId) {
        ChatMedia media = mediaId == null ? null : repo.findById(mediaId).orElse(null);
        // Someone else's photo looks the same as a missing one, so ids can't be probed
        if (media == null || !media.getUploaderUsername().equals(sender)) {
            throw new ChatSendException("INVALID_MEDIA", "That photo can't be sent. Pick it again.");
        }
        if (media.isAttached()) {
            throw new ChatSendException("MEDIA_IN_USE", "That photo was already sent");
        }
        return media;
    }

    /**
     * Marks the photo as used, in the transaction that saves the message, so a rolled-back save frees
     * it again. False if another send claimed it first.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean claim(String sender, Long mediaId) {
        return repo.markAttached(mediaId, sender) == 1;
    }

    // ── Viewing ────────────────────────────────────────────────────────────

    /** Expiring URLs for these photos, by id. Ids that are missing or fail to sign are left out. */
    public Map<Long, String> viewUrls(Collection<Long> mediaIds) {
        if (mediaIds.isEmpty()) return Map.of();
        Map<Long, String> urls = new HashMap<>();
        for (ChatMedia media : repo.findAllById(mediaIds)) {
            viewUrl(media).ifPresent(url -> urls.put(media.getId(), url));
        }
        return urls;
    }

    public Optional<String> viewUrl(Long mediaId) {
        return mediaId == null ? Optional.empty() : repo.findById(mediaId).flatMap(this::viewUrl);
    }

    private Optional<String> viewUrl(ChatMedia media) {
        try {
            return Optional.of(storage.viewUrl(media, urlTtl));
        } catch (RuntimeException e) {
            // One photo that can't be signed shouldn't fail a whole history page
            logger.warn("[Media] couldn't make a URL for chat photo {}", media.getId(), e);
            return Optional.empty();
        }
    }

    /**
     * Makes a backend-relative URL ("/api/…", from local storage) absolute, using the address the
     * current HTTP request came in on. For the admin dashboard, which runs on another origin; the
     * app resolves relative URLs itself. Left as is outside a request, or when already absolute.
     */
    public static String absoluteForCurrentRequest(String url) {
        if (url == null || !url.startsWith("/")) return url;
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) return url;
        return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString() + url;
    }
}
