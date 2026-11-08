package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.ChatMessageResponse;
import com.ureclive.urec_live_backend.dto.SendChatMessageRequest;
import com.ureclive.urec_live_backend.entity.ChatChannel;
import com.ureclive.urec_live_backend.entity.ChatMessageBase;
import com.ureclive.urec_live_backend.entity.CommunityMessage;
import com.ureclive.urec_live_backend.entity.DirectMessage;
import com.ureclive.urec_live_backend.entity.MessageKind;
import com.ureclive.urec_live_backend.entity.MessageReaction;
import com.ureclive.urec_live_backend.repository.ChatMessageReportRepository;
import com.ureclive.urec_live_backend.repository.CommunityMessageRepository;
import com.ureclive.urec_live_backend.repository.DirectMessageRepository;
import com.ureclive.urec_live_backend.repository.MessageReactionRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The races the database guards against, driven one step at a time: a lookup that misses because
 * the other request hasn't committed yet, then an insert the unique constraint rejects.
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    private static final String GIF = "https://static.klipy.com/ii/abc123/md.webp";

    @Mock private CommunityMessageRepository communityRepo;
    @Mock private DirectMessageRepository dmRepo;
    @Mock private MessageReactionRepository reactionRepo;
    @Mock private ChatMessageReportRepository reportRepo;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private KlipyClient klipy;
    @Mock private ActivityLogService activityLogService;

    @InjectMocks private ChatMessageService service;

    private static DataIntegrityViolationException duplicateKey() {
        return new DataIntegrityViolationException("duplicate key value violates unique constraint");
    }

    // ── Sends ──────────────────────────────────────────────────────────────

    @Test
    void aCommunityRetryThatLosesTheInsertRaceSendsTheCopyAlreadySaved() {
        CommunityMessage winner = saved(new CommunityMessage(), 41L, "alice", "c-1");
        when(klipy.isAllowedMediaUrl(anyString())).thenReturn(true);
        when(communityRepo.findFirstBySenderUsernameAndClientId("alice", "c-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(communityRepo.save(any(CommunityMessage.class))).thenThrow(duplicateKey());

        ChatMessageResponse response = service.sendCommunity("alice", gif("c-1"));

        assertEquals(41L, response.id);
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(ChatMessageService.COMMUNITY_TOPIC), sent.capture());
        assertEquals(41L, ((ChatMessageResponse) sent.getValue()).id);
        // The share was already counted for the copy that won
        verify(klipy, never()).reportShare(any(), any(), any());
    }

    @Test
    void aDirectRetryThatLosesTheInsertRaceSendsTheCopyAlreadySaved() {
        DirectMessage winner = saved(new DirectMessage(), 52L, "alice", "d-1");
        winner.setRecipientUsername("bob");
        when(userRepository.existsByUsername("bob")).thenReturn(true);
        when(dmRepo.findFirstBySenderUsernameAndClientId("alice", "d-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(dmRepo.save(any(DirectMessage.class))).thenThrow(duplicateKey());

        SendChatMessageRequest request = text("d-1", "hi bob");
        request.setRecipient("bob");
        assertEquals(52L, service.sendDirect("alice", request).id);

        verify(messagingTemplate).convertAndSendToUser(eq("bob"), eq(ChatMessageService.USER_QUEUE), any(Object.class));
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq(ChatMessageService.USER_QUEUE), any(Object.class));
    }

    @Test
    void anInsertFailureThatIsntAClientIdClashStillFails() {
        when(communityRepo.save(any(CommunityMessage.class))).thenThrow(duplicateKey());
        assertThrows(DataIntegrityViolationException.class, () -> service.sendCommunity("alice", text(null, "hi")));

        when(communityRepo.findFirstBySenderUsernameAndClientId("alice", "c-2")).thenReturn(Optional.empty());
        assertThrows(DataIntegrityViolationException.class, () -> service.sendCommunity("alice", text("c-2", "hi")));
        verifyNoInteractions(messagingTemplate);
    }

    // ── Reactions ──────────────────────────────────────────────────────────

    @Test
    void aDoubleTapThatLosesTheInsertRaceKeepsTheOneReaction() {
        MessageReaction first = new MessageReaction(ChatChannel.COMMUNITY, 7L, "bob", "👍");
        reactingTo(saved(new CommunityMessage(), 7L, "alice", null), first);
        when(reactionRepo.save(any(MessageReaction.class))).thenThrow(duplicateKey());

        assertEquals(7L, service.react(ChatChannel.COMMUNITY, 7L, "bob", "👍").id);

        // Only the rejected insert; the reaction the first tap saved is already right
        verify(reactionRepo, times(1)).save(any(MessageReaction.class));
        assertEquals("👍", first.getEmoji());
        verify(messagingTemplate).convertAndSend(eq(ChatMessageService.COMMUNITY_TOPIC), any(Object.class));
    }

    @Test
    void twoQuickTapsOnDifferentEmojiEndOnTheLaterOne() {
        MessageReaction first = new MessageReaction(ChatChannel.COMMUNITY, 7L, "bob", "👍");
        reactingTo(saved(new CommunityMessage(), 7L, "alice", null), first);
        when(reactionRepo.save(any(MessageReaction.class)))
                .thenThrow(duplicateKey())
                .thenAnswer(inv -> inv.getArgument(0));

        service.react(ChatChannel.COMMUNITY, 7L, "bob", "🔥");

        assertEquals("🔥", first.getEmoji());
        verify(reactionRepo).save(first);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** The message exists, and bob's reaction lookup misses once before the other tap's row shows up. */
    private void reactingTo(CommunityMessage message, MessageReaction savedByTheOtherTap) {
        when(communityRepo.findById(message.getId())).thenReturn(Optional.of(message));
        when(reactionRepo.findByChannelAndMessageIdAndUsername(ChatChannel.COMMUNITY, message.getId(), "bob"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedByTheOtherTap));
    }

    private static <M extends ChatMessageBase> M saved(M message, long id, String sender, String clientId) {
        message.setSenderUsername(sender);
        message.setContent("already saved");
        message.setClientId(clientId);
        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(message, "sentAt", Instant.parse("2026-11-03T12:00:00Z"));
        return message;
    }

    private static SendChatMessageRequest text(String clientId, String content) {
        SendChatMessageRequest request = new SendChatMessageRequest();
        request.setClientId(clientId);
        request.setContent(content);
        return request;
    }

    private static SendChatMessageRequest gif(String clientId) {
        SendChatMessageRequest request = new SendChatMessageRequest();
        request.setClientId(clientId);
        request.setKind(MessageKind.GIF);
        request.setMedia(new SendChatMessageRequest.MediaPayload(GIF, null, 220, 180, "abc123"));
        return request;
    }
}
