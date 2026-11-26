package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.ChatMedia;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Keeps chat photos in a folder on this machine, for development and tests (media.storage=local).
 * Photos are served by {@code GET /api/messaging/media/{id}/raw?exp=…&sig=…}, where sig is an HMAC
 * of the id and expiry, so the URL works like an expiring presigned R2 URL. The URLs are relative
 * to the backend; the app resolves them against its backend address.
 */
public class LocalDiskMediaStorage implements MediaStorage {

    public static final String RAW_PATH = "/api/messaging/media/%d/raw";

    private final Path root;
    private final byte[] signingKey;
    private final Clock clock;

    public LocalDiskMediaStorage(Path root, String secret, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        // Its own key, so a URL signature can never double as anything else signed with the secret
        this.signingKey = ("chat-media-url:" + secret).getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public Path root() {
        return root;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            // Write then move, so a reader never sees half a file
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temp, bytes);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't save chat photo " + key, e);
        }
    }

    /** The stored bytes, or empty if the file is gone. */
    public Optional<byte[]> read(String key) {
        try {
            return Optional.of(Files.readAllBytes(resolve(key)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read chat photo " + key, e);
        }
    }

    @Override
    public String viewUrl(ChatMedia media, Duration ttl) {
        long expires = clock.instant().plus(ttl).getEpochSecond();
        return String.format(RAW_PATH, media.getId()) + "?exp=" + expires + "&sig=" + sign(media.getId(), expires);
    }

    /** True if the signature is ours for this id and expiry, and the expiry hasn't passed. */
    public boolean isValid(long mediaId, long expires, String signature) {
        if (signature == null || expires < clock.instant().getEpochSecond()) return false;
        byte[] expected = sign(mediaId, expires).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
    }

    /** Seconds until the expiry, for the response's Cache-Control. */
    public long secondsUntil(long expires) {
        return Math.max(0, expires - clock.instant().getEpochSecond());
    }

    private String sign(long mediaId, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            byte[] digest = mac.doFinal((mediaId + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** Keys are made by the server, but a key can still never point outside the folder. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Invalid storage key: " + key);
        }
        return path;
    }
}
