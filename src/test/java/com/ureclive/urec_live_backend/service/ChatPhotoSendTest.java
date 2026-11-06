package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.ChatMessageResponse;
import com.ureclive.urec_live_backend.dto.SendChatMessageRequest;
import com.ureclive.urec_live_backend.entity.ChatChannel;
import com.ureclive.urec_live_backend.entity.ChatMedia;
import com.ureclive.urec_live_backend.entity.CommunityMessage;
import com.ureclive.urec_live_backend.entity.MessageKind;
import com.ureclive.urec_live_backend.entity.MessageMedia;
import com.ureclive.urec_live_backend.repository.ChatMessageReportRepository;
import com.ureclive.urec_live_backend.repository.CommunityMessageRepository;
import com.ureclive.urec_live_backend.repository.DirectMessageRepository;
import com.ureclive.urec_live_backend.repository.MessageReactionRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Photo sends on branch 1's send path, one step at a time: the clientId lookup before the photo
 * checks, the claim and the save in one transaction, and a retry racing its own first copy.
 */
@ExtendWith(MockitoExtension.class)
class ChatPhotoSendTest {

    @Mock private CommunityMessageRepository communityRepo;
    @Mock private DirectMessageRepository dmRepo;
    @Mock private MessageReactionRepository reactionRepo;
    @Mock private ChatMessageReportRepository reportRepo;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private KlipyClient klipy;
    @Mock private ActivityLogService activityLogService;
    @Mock private ChatMediaService media;
    @Mock private TransactionTemplate transactions;

    @InjectMocks private ChatMessageService service;

    /** True while the mocked transaction's callback runs. */
    private final AtomicBoolean inTransaction = new AtomicBoolean();

    @BeforeEach
    void runTransactionsInline() {
        lenient().doAnswer(inv -> {
            Consumer<TransactionStatus> work = inv.getArgument(0);
            inTransaction.set(true);
            try {
                work.accept(null);
            } finally {
                inTransaction.set(false);
            }
            return null;
        }).when(transactions).executeWithoutResult(any());
    }

    @Test
    void thePhotoIsClaimedAndTheMessageSavedInOneTransaction() {
        when(communityRepo.findFirstBySenderUsernameAndClientId("alice", "p-1")).thenReturn(Optional.empty());
        when(media.attachable("alice", 5L)).thenReturn(upload(5L, "alice"));
        when(media.claim("alice", 5L)).thenAnswer(inv -> {
            assertTrue(inTransaction.get(), "claimed inside the transaction");
            return true;
        });
        when(communityRepo.save(any(CommunityMessage.class))).thenAnswer(inv -> {
            assertTrue(inTransaction.get(), "saved inside the same transaction");
            CommunityMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 70L);
            ReflectionTestUtils.setField(m, "sentAt", Instant.parse("2026-11-05T12:00:00Z"));
            return m;
        });
        when(media.viewUrls(Set.of(5L))).thenReturn(Map.of(5L, "https://r2.example/photo-5"));

        ChatMessageResponse response = service.sendCommunity("alice", photo("p-1", 5L, "  leg day  "));

        InOrder order = inOrder(media, communityRepo);
        order.verify(media).attachable("alice", 5L);
        order.verify(media).claim("alice", 5L);
        order.verify(communityRepo).save(any(CommunityMessage.class));
        assertEquals("IMAGE", response.kind);
        assertEquals("leg day", response.content);
        assertEquals("https://r2.example/photo-5", response.media.url);
        assertEquals(5L, response.media.mediaId);
        assertEquals(640, response.media.width);
        assertEquals(480, response.media.height);
        assertNull(response.media.previewUrl);
        verify(messagingTemplate).convertAndSend(eq(ChatMessageService.COMMUNITY_TOPIC), any(Object.class));
        // Photos aren't KLIPY items: no share is reported
        verifyNoInteractions(klipy);
    }

    @Test
    void whenAnotherSendClaimedThePhotoNothingIsSaved() {
        when(communityRepo.findFirstBySenderUsernameAndClientId("alice", "p-2")).thenReturn(Optional.empty());
        when(media.attachable("alice", 5L)).thenReturn(upload(5L, "alice"));
        when(media.claim("alice", 5L)).thenReturn(false);

        ChatSendException e = assertThrows(ChatSendException.class,
                () -> service.sendCommunity("alice", photo("p-2", 5L, null)));

        assertEquals("MEDIA_IN_USE", e.getCode());
        verify(communityRepo, never()).save(any());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void aRetryRacingItsFirstCopyForThePhotoSendsThatCopy() {
        CommunityMessage winner = savedPhoto(81L, "alice", "p-3", 5L);
        // Both copies miss the lookup; the first then claims the photo and saves before this one claims
        when(communityRepo.findFirstBySenderUsernameAndClientId("alice", "p-3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(media.attachable("alice", 5L)).thenReturn(upload(5L, "alice"));
        when(media.claim("alice", 5L)).thenReturn(false);
        when(media.viewUrls(Set.of(5L))).thenReturn(Map.of(5L, "https://r2.example/photo-5"));

        ChatMessageResponse response = service.sendCommunity("alice", photo("p-3", 5L, "hi"));

        assertEquals(81L, response.id);
        assertEquals("https://r2.example/photo-5", response.media.url);
        verify(communityRepo, never()).save(any());
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(ChatMessageService.COMMUNITY_TOPIC), sent.capture());
        assertEquals(81L, ((ChatMessageResponse) sent.getValue()).id);
    }

    @Test
    void aRetryOfASavedPhotoSendIsFoundBeforeThePhotoIsChecked() {
        CommunityMessage saved = savedPhoto(82L, "alice", "p-4", 5L);
        when(communityRepo.findFirstBySenderUsernameAndClientId("alice", "p-4")).thenReturn(Optional.of(saved));
        when(media.viewUrls(Set.of(5L))).thenReturn(Map.of(5L, "https://r2.example/photo-5"));

        ChatMessageResponse response = service.sendCommunity("alice", photo("p-4", 5L, "hi"));

        assertEquals(82L, response.id);
        // The photo is attached to that message by now, so checking it would wrongly fail the retry
        verify(media, never()).attachable(any(), any());
        verify(media, never()).claim(any(), any());
        verifyNoInteractions(transactions);
    }

    @Test
    void aBadCaptionFailsBeforeThePhotoIsTouched() {
        when(communityRepo.findFirstBySenderUsernameAndClientId("alice", "p-5")).thenReturn(Optional.empty());
        ChatSendException e = assertThrows(ChatSendException.class,
                () -> service.sendCommunity("alice", photo("p-5", 5L, "x".repeat(501))));
        assertEquals("TOO_LONG", e.getCode());
        verifyNoInteractions(media, transactions);
    }

    @Test
    void historyMakesPhotoUrlsInOneCallAndOnlyForVisiblePhotos() {
        CommunityMessage first = savedPhoto(1L, "alice", null, 11L);
        CommunityMessage deleted = savedPhoto(2L, "alice", null, 12L);
        deleted.setDeletedAt(Instant.parse("2026-11-05T12:30:00Z"));
        CommunityMessage unsignable = savedPhoto(3L, "bob", null, 13L);
        CommunityMessage text = new CommunityMessage();
        text.setSenderUsername("bob");
        text.setContent("nice");
        ReflectionTestUtils.setField(text, "id", 4L);
        ReflectionTestUtils.setField(text, "sentAt", Instant.parse("2026-11-05T12:00:00Z"));
        when(communityRepo.findByIdLessThanOrderByIdDesc(anyLong(), any()))
                .thenReturn(List.of(text, unsignable, deleted, first));
        when(media.viewUrls(Set.of(11L, 13L))).thenReturn(Map.of(11L, "https://r2.example/11"));

        List<ChatMessageResponse> page = service.communityHistory(null, null);

        verify(media, times(1)).viewUrls(any());
        assertEquals(List.of(1L, 2L, 3L, 4L), page.stream().map(r -> r.id).toList());
        assertEquals("https://r2.example/11", page.get(0).media.url);
        assertNull(page.get(1).media, "deleted: no media at all");
        assertEquals("", page.get(1).content);
        assertNull(page.get(2).media, "a photo whose URL couldn't be made shows as unavailable");
        assertEquals("IMAGE", page.get(2).kind);
        assertNull(page.get(3).media);
    }

    @Test
    void deletingAPhotoBroadcastsItWithoutAUrl() {
        CommunityMessage message = savedPhoto(90L, "alice", null, 5L);
        when(communityRepo.findById(90L)).thenReturn(Optional.of(message));

        ChatMessageResponse response = service.deleteOwn(ChatChannel.COMMUNITY, 90L, "alice");

        assertTrue(response.deleted);
        assertNull(response.media);
        verify(media, never()).viewUrls(any());
    }

    @Test
    void theInboxCallsAPhotoAPhoto() {
        assertEquals("Photo", ChatMessageService.preview(savedPhoto(1L, "alice", null, 5L)));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static ChatMedia upload(long id, String uploader) {
        ChatMedia m = new ChatMedia(uploader, "chat-media/" + id + ".jpg", "image/jpeg", 1234, 640, 480);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private static CommunityMessage savedPhoto(long id, String sender, String clientId, long uploadId) {
        CommunityMessage m = new CommunityMessage();
        m.setSenderUsername(sender);
        m.setContent("caption");
        m.setClientId(clientId);
        m.setKind(MessageKind.IMAGE);
        m.setMedia(MessageMedia.photo(upload(uploadId, sender)));
        ReflectionTestUtils.setField(m, "id", id);
        ReflectionTestUtils.setField(m, "sentAt", Instant.parse("2026-11-05T12:00:00Z"));
        return m;
    }

    private static SendChatMessageRequest photo(String clientId, Long mediaId, String caption) {
        SendChatMessageRequest request = new SendChatMessageRequest();
        request.setClientId(clientId);
        request.setKind(MessageKind.IMAGE);
        request.setContent(caption);
        SendChatMessageRequest.MediaPayload payload = new SendChatMessageRequest.MediaPayload();
        payload.setMediaId(mediaId);
        request.setMedia(payload);
        return request;
    }
}
