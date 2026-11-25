package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.ChatMedia;
import com.ureclive.urec_live_backend.repository.ChatMediaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.ureclive.urec_live_backend.service.ChatPhotoFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatMediaServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(120);

    private ChatMediaRepository repo;
    private MediaStorage storage;
    private ChatMediaService service;

    @BeforeEach
    void setUp() {
        repo = mock(ChatMediaRepository.class);
        storage = mock(MediaStorage.class);
        when(repo.save(any(ChatMedia.class))).thenAnswer(inv -> {
            ChatMedia m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 99L);
            return m;
        });
        service = new ChatMediaService(repo, storage, new ChatImageProcessor(), TTL, 3, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes);
    }

    private static ChatMedia stored(long id, String uploader, boolean attached) {
        ChatMedia m = new ChatMedia(uploader, "chat-media/" + id + ".jpg", "image/jpeg", 10, 40, 30);
        ReflectionTestUtils.setField(m, "id", id);
        ReflectionTestUtils.setField(m, "attached", attached);
        return m;
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(status, e.getStatusCode());
        verifyNoInteractions(storage);
        verify(repo, never()).save(any());
    }

    // ── Uploading ──────────────────────────────────────────────────────────

    @Test
    void aGoodPhotoIsReencodedStoredAndRecorded() {
        ChatMedia saved = service.upload("alice", file(withExif(jpeg(300, 200, Color.CYAN), 1)));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(storage).put(key.capture(), bytes.capture(), eq("image/jpeg"));
        assertTrue(key.getValue().matches("chat-media/[0-9a-f-]{36}\\.jpg"), key.getValue());
        assertFalse(hasExif(bytes.getValue()), "the stored bytes are the re-encoded copy");

        assertEquals(99L, saved.getId());
        assertEquals("alice", saved.getUploaderUsername());
        assertEquals(key.getValue(), saved.getStorageKey());
        assertEquals(bytes.getValue().length, saved.getSizeBytes());
        assertEquals(300, saved.getWidth());
        assertEquals(200, saved.getHeight());
        assertFalse(saved.isAttached());
    }

    @Test
    void uploadsAreCheckedBeforeAnythingIsStored() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("alice", null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("alice", file(new byte[0])));
        assertStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, () -> service.upload("alice", file(png(10, 10))));

        byte[] tooBig = new byte[(int) ChatMediaService.MAX_UPLOAD_BYTES + 1];
        System.arraycopy(jpeg(4, 4, Color.RED), 0, tooBig, 0, 3);
        assertStatus(HttpStatus.PAYLOAD_TOO_LARGE, () -> service.upload("alice", file(tooBig)));

        byte[] corrupt = new byte[500];
        corrupt[0] = (byte) 0xFF;
        corrupt[1] = (byte) 0xD8;
        corrupt[2] = (byte) 0xFF;
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("alice", file(corrupt)));
    }

    @Test
    void exactlyFiveMegabytesIsAllowed() {
        byte[] photo = jpeg(64, 64, Color.RED);
        // Padding after the end-of-image marker is ignored by decoders
        byte[] padded = new byte[(int) ChatMediaService.MAX_UPLOAD_BYTES];
        System.arraycopy(photo, 0, padded, 0, photo.length);
        assertNotNull(service.upload("alice", file(padded)));
    }

    @Test
    void membersCantPileUpUnsentUploads() {
        when(repo.countByUploaderUsernameAndAttachedFalseAndCreatedAtAfter("alice", NOW.minus(Duration.ofHours(1))))
                .thenReturn(3L);
        assertStatus(HttpStatus.TOO_MANY_REQUESTS, () -> service.upload("alice", file(jpeg(8, 8, Color.RED))));

        when(repo.countByUploaderUsernameAndAttachedFalseAndCreatedAtAfter("alice", NOW.minus(Duration.ofHours(1))))
                .thenReturn(2L);
        assertNotNull(service.upload("alice", file(jpeg(8, 8, Color.RED))));
    }

    @Test
    void aStorageFailureSavesNoRecord() {
        doThrow(new IllegalStateException("bucket down")).when(storage).put(anyString(), any(), anyString());
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.upload("alice", file(jpeg(8, 8, Color.RED))));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        verify(repo, never()).save(any());
    }

    // ── Attaching ──────────────────────────────────────────────────────────

    @Test
    void onlyTheUploadersUnsentPhotoCanBeAttached() {
        when(repo.findById(1L)).thenReturn(Optional.of(stored(1, "alice", false)));
        when(repo.findById(2L)).thenReturn(Optional.of(stored(2, "alice", true)));
        when(repo.findById(3L)).thenReturn(Optional.empty());

        assertEquals(1L, service.attachable("alice", 1L).getId());
        assertEquals("INVALID_MEDIA", assertThrows(ChatSendException.class, () -> service.attachable("bob", 1L)).getCode());
        assertEquals("MEDIA_IN_USE", assertThrows(ChatSendException.class, () -> service.attachable("alice", 2L)).getCode());
        assertEquals("INVALID_MEDIA", assertThrows(ChatSendException.class, () -> service.attachable("alice", 3L)).getCode());
        assertEquals("INVALID_MEDIA", assertThrows(ChatSendException.class, () -> service.attachable("alice", null)).getCode());
    }

    @Test
    void claimingIsTheConditionalUpdate() {
        when(repo.markAttached(1L, "alice")).thenReturn(1);
        when(repo.markAttached(2L, "alice")).thenReturn(0);
        assertTrue(service.claim("alice", 1L));
        assertFalse(service.claim("alice", 2L));
    }

    // ── Viewing ────────────────────────────────────────────────────────────

    @Test
    void urlsAreMadeWithTheConfiguredLifetimeAndOneFailureDoesntSinkThePage() {
        ChatMedia ok = stored(1, "alice", true);
        ChatMedia broken = stored(2, "alice", true);
        when(repo.findAllById(Set.of(1L, 2L))).thenReturn(List.of(ok, broken));
        when(storage.viewUrl(ok, TTL)).thenReturn("https://r2/1");
        when(storage.viewUrl(broken, TTL)).thenThrow(new IllegalStateException("signing failed"));

        assertEquals(Map.of(1L, "https://r2/1"), service.viewUrls(Set.of(1L, 2L)));
        assertEquals(Map.of(), service.viewUrls(Set.of()));
        verify(repo, times(1)).findAllById(any());

        when(repo.findById(1L)).thenReturn(Optional.of(ok));
        assertEquals(Optional.of("https://r2/1"), service.viewUrl(1L));
        assertEquals(Optional.empty(), service.viewUrl(null));
    }

    @Test
    void relativeUrlsBecomeAbsoluteOnlyInsideARequest() {
        assertEquals("/api/messaging/media/1/raw?x", ChatMediaService.absoluteForCurrentRequest("/api/messaging/media/1/raw?x"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals("http://localhost:8080/api/messaging/media/1/raw?x",
                ChatMediaService.absoluteForCurrentRequest("/api/messaging/media/1/raw?x"));
        assertEquals("https://r2/1", ChatMediaService.absoluteForCurrentRequest("https://r2/1"));
        assertNull(ChatMediaService.absoluteForCurrentRequest(null));
    }
}
