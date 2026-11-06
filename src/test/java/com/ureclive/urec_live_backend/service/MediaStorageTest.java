package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.config.MediaStorageConfig;
import com.ureclive.urec_live_backend.entity.ChatMedia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Both storage backends, without a network or a real bucket. */
class MediaStorageTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final Pattern LOCAL_URL = Pattern.compile("/api/messaging/media/(\\d+)/raw\\?exp=(\\d+)&sig=([A-Za-z0-9_-]+)");

    @TempDir Path dir;

    private static ChatMedia media(long id, String key) {
        ChatMedia media = new ChatMedia("alice", key, "image/jpeg", 10, 4, 3);
        ReflectionTestUtils.setField(media, "id", id);
        return media;
    }

    private LocalDiskMediaStorage local(Instant now) {
        return new LocalDiskMediaStorage(dir, "test-secret", Clock.fixed(now, ZoneOffset.UTC));
    }

    // ── Local disk ─────────────────────────────────────────────────────────

    @Test
    void localStorageKeepsFilesUnderItsFolder() throws Exception {
        LocalDiskMediaStorage storage = local(NOW);
        byte[] bytes = "photo bytes".getBytes(StandardCharsets.UTF_8);
        storage.put("chat-media/abc.jpg", bytes, "image/jpeg");

        assertArrayEquals(bytes, Files.readAllBytes(dir.resolve("chat-media/abc.jpg")));
        assertArrayEquals(bytes, storage.read("chat-media/abc.jpg").orElseThrow());
        assertTrue(storage.read("chat-media/missing.jpg").isEmpty());
        try (var files = Files.list(dir.resolve("chat-media"))) {
            assertEquals(1, files.count(), "no temp file left behind");
        }
    }

    @Test
    void localStorageKeysCantEscapeItsFolder() {
        LocalDiskMediaStorage storage = local(NOW);
        assertThrows(IllegalArgumentException.class, () -> storage.put("../outside.jpg", new byte[1], "image/jpeg"));
        assertThrows(IllegalArgumentException.class, () -> storage.read("chat-media/../../outside.jpg"));
        assertThrows(IllegalArgumentException.class, () -> storage.read(""));
    }

    @Test
    void localUrlsAreSignedAndExpire() {
        LocalDiskMediaStorage storage = local(NOW);
        String url = storage.viewUrl(media(7, "chat-media/a.jpg"), Duration.ofMinutes(30));

        Matcher m = LOCAL_URL.matcher(url);
        assertTrue(m.matches(), url);
        assertEquals("7", m.group(1));
        long exp = Long.parseLong(m.group(2));
        assertEquals(NOW.plusSeconds(1800).getEpochSecond(), exp);
        String sig = m.group(3);

        assertTrue(storage.isValid(7, exp, sig));
        assertEquals(1800, storage.secondsUntil(exp));
        assertFalse(storage.isValid(8, exp, sig), "another photo");
        assertFalse(storage.isValid(7, exp + 60, sig), "a later expiry");
        assertFalse(storage.isValid(7, exp, sig.substring(1) + "A"), "a changed signature");
        assertFalse(storage.isValid(7, exp, null));
        assertFalse(storage.isValid(7, exp, ""));

        // Still good up to the second it expires, then not
        assertTrue(local(NOW.plusSeconds(1800)).isValid(7, exp, sig));
        assertFalse(local(NOW.plusSeconds(1801)).isValid(7, exp, sig));
        // A server with another secret doesn't accept it
        assertFalse(new LocalDiskMediaStorage(dir, "other-secret", Clock.fixed(NOW, ZoneOffset.UTC)).isValid(7, exp, sig));
    }

    // ── S3 / R2 ────────────────────────────────────────────────────────────

    @Test
    void s3UrlsArePresignedForTheBucketAndExpire() {
        String endpoint = "https://0123456789abcdef.r2.cloudflarestorage.com";
        try (S3Presigner presigner = S3MediaStorage.presigner(endpoint, "AKIDEXAMPLE", "secret")) {
            S3MediaStorage storage = new S3MediaStorage(mock(S3Client.class), presigner, "urec-chat");

            URI url = URI.create(storage.viewUrl(media(3, "chat-media/xyz.jpg"), Duration.ofMinutes(120)));

            assertEquals("https", url.getScheme());
            assertEquals("0123456789abcdef.r2.cloudflarestorage.com", url.getHost());
            assertEquals("/urec-chat/chat-media/xyz.jpg", url.getPath(), "path-style: bucket, then key");
            String query = url.getQuery();
            assertTrue(query.contains("X-Amz-Expires=7200"), query);
            assertTrue(query.contains("X-Amz-Credential=AKIDEXAMPLE/"), query);
            assertTrue(query.contains("/auto/s3/aws4_request"), "signed for region auto: " + query);
            assertTrue(query.matches(".*X-Amz-Signature=[0-9a-f]{64}.*"), query);
            assertFalse(query.contains("secret"), "the secret key never appears in a URL");
        }
    }

    @Test
    void s3UploadsSetTheTypeAndCaching() throws Exception {
        S3Client client = mock(S3Client.class);
        S3MediaStorage storage = new S3MediaStorage(client, mock(S3Presigner.class), "urec-chat");
        byte[] bytes = {1, 2, 3};

        storage.put("chat-media/xyz.jpg", bytes, "image/jpeg");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertEquals("urec-chat", request.getValue().bucket());
        assertEquals("chat-media/xyz.jpg", request.getValue().key());
        assertEquals("image/jpeg", request.getValue().contentType());
        assertEquals(S3MediaStorage.CACHE_CONTROL, request.getValue().cacheControl());
        assertArrayEquals(bytes, body.getValue().contentStreamProvider().newStream().readAllBytes());
    }

    @Test
    void closingS3StorageClosesItsClients() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        new S3MediaStorage(client, presigner, "b").close();
        verify(client).close();
        verify(presigner).close();
    }

    // ── Choosing one ───────────────────────────────────────────────────────

    @Test
    void localIsChosenWithoutAnyBucketSettings() {
        MediaStorage storage = MediaStorageConfig.create(
                " Local ", dir.toString(), "", "", "", "", "secret");
        assertInstanceOf(LocalDiskMediaStorage.class, storage);
        assertEquals(dir.toAbsolutePath().normalize(), ((LocalDiskMediaStorage) storage).root());
    }

    @Test
    void s3NamesEveryMissingSetting() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                MediaStorageConfig.create(
                        "s3", dir.toString(), "https://x.r2.cloudflarestorage.com", " ", null, "key", "secret"));
        assertTrue(e.getMessage().contains("MEDIA_S3_BUCKET"), e.getMessage());
        assertTrue(e.getMessage().contains("MEDIA_S3_ACCESS_KEY"), e.getMessage());
        assertFalse(e.getMessage().contains("MEDIA_S3_ENDPOINT"), e.getMessage());
        assertFalse(e.getMessage().contains("MEDIA_S3_SECRET_KEY"), e.getMessage());
    }

    @Test
    void s3WithEverySettingBuildsWithoutConnecting() {
        MediaStorage storage = MediaStorageConfig.create(
                "S3", dir.toString(), "https://x.r2.cloudflarestorage.com", "b", "AKID", "secret", "jwt");
        assertInstanceOf(S3MediaStorage.class, storage);
        ((S3MediaStorage) storage).close();
    }

    @Test
    void anUnknownStorageTypeStopsStartup() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                MediaStorageConfig.create(
                        "dropbox", dir.toString(), "", "", "", "", "secret"));
        assertTrue(e.getMessage().contains("dropbox"));
    }
}
