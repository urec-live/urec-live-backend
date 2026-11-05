package com.ureclive.urec_live_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ureclive.urec_live_backend.dto.SendChatMessageRequest;
import com.ureclive.urec_live_backend.entity.ChatChannel;
import com.ureclive.urec_live_backend.entity.ChatMessageBase;
import com.ureclive.urec_live_backend.entity.ChatMessageReport;
import com.ureclive.urec_live_backend.entity.CommunityMessage;
import com.ureclive.urec_live_backend.entity.DirectMessage;
import com.ureclive.urec_live_backend.entity.MessageKind;
import com.ureclive.urec_live_backend.entity.ReportStatus;
import com.ureclive.urec_live_backend.repository.ChatMessageReportRepository;
import com.ureclive.urec_live_backend.repository.CommunityMessageRepository;
import com.ureclive.urec_live_backend.repository.DirectMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.MimeTypeUtils;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Community chat and DMs end to end: STOMP sends (called on the controller, and once through the
 * real inbound channel), what gets broadcast, paged history, reactions, deleting, reporting, the
 * admin report queue and the GIF search proxy.
 */
class ChatMessagingApiIntegrationTest extends ApiIntegrationTestSupport {

    private static final String KLIPY_GIF = "https://static.klipy.com/ii/abc123/md.webp";
    private static final String KLIPY_PREVIEW = "https://static.klipy.com/ii/abc123/sm.webp";
    private static final String HISTORY = "/api/messaging/community/history";
    private static final String CHAT_REPORTS = "/api/admin/chat-reports";
    private static final String ALICE = "chat_alice";
    private static final String BOB = "chat_bob";
    private static final String CAROL = "chat_carol";

    @Autowired private ChatMessagingController controller;
    @Autowired @Qualifier("brokerChannel") private AbstractSubscribableChannel brokerChannel;
    @Autowired @Qualifier("clientInboundChannel") private MessageChannel clientInboundChannel;
    @Autowired private CommunityMessageRepository communityRepo;
    @Autowired private DirectMessageRepository dmRepo;
    @Autowired private ChatMessageReportRepository reportRepo;
    @Autowired private JdbcTemplate jdbc;

    private String aliceToken;
    private String bobToken;
    private String carolToken;
    private String adminToken;
    private int clientIds;

    @BeforeEach
    void setUp() {
        aliceToken = tokenFor(ALICE, "USER");
        bobToken = tokenFor(BOB, "USER");
        carolToken = tokenFor(CAROL, "USER");
        adminToken = tokenFor("chat_admin", "ADMIN");
    }

    // ── Sending ────────────────────────────────────────────────────────────

    @Test
    void textMessageIsTrimmedSavedAndBroadcastWithItsClientId() throws Exception {
        String clientId = nextClientId();
        List<Sent> sent = capture(() -> controller.sendCommunity(text(clientId, "  hello gym  "), as(ALICE)));

        JsonNode message = only(sent, "/topic/chat.community");
        assertEquals("hello gym", message.get("content").asText());
        assertEquals("TEXT", message.get("kind").asText());
        assertEquals("COMMUNITY", message.get("type").asText());
        assertEquals(clientId, message.get("clientId").asText());
        assertEquals(ALICE, message.get("senderUsername").asText());
        assertFalse(message.get("deleted").asBoolean());
        assertTrue(message.get("reactions").isEmpty());
        assertTrue(communityRepo.findFirstBySenderUsernameAndClientId(ALICE, clientId).isPresent());
    }

    @Test
    void olderAppsThatSendOnlyContentStillWork() throws Exception {
        SendChatMessageRequest legacy = new SendChatMessageRequest();
        legacy.setContent("from an old app");
        JsonNode message = only(capture(() -> controller.sendCommunity(legacy, as(ALICE))), "/topic/chat.community");
        assertEquals("TEXT", message.get("kind").asText());
        assertEquals("from an old app", message.get("content").asText());
    }

    @Test
    void rejectedSendsGoToTheSendersErrorQueueAndSaveNothing() throws Exception {
        assertRejected(text(nextClientId(), "a".repeat(501)), "TOO_LONG");
        assertRejected(text(nextClientId(), "   "), "EMPTY");
        assertRejected(text(nextClientId(), null), "EMPTY");
        assertRejected(text("not a valid id!", "hi"), "INVALID_CLIENT_ID");
        assertRejected(text("x".repeat(37), "hi"), "INVALID_CLIENT_ID");
    }

    @Test
    void exactlyFiveHundredCharactersIsAllowed() throws Exception {
        JsonNode message = only(capture(() -> controller.sendCommunity(text(nextClientId(), "b".repeat(500)), as(ALICE))),
                "/topic/chat.community");
        assertEquals(500, message.get("content").asText().length());
    }

    @Test
    void gifIsSavedWithItsMediaAndNoText() throws Exception {
        SendChatMessageRequest request = gif(nextClientId(), KLIPY_GIF);
        request.setContent("captions are ignored");
        JsonNode message = only(capture(() -> controller.sendCommunity(request, as(ALICE))), "/topic/chat.community");

        assertEquals("GIF", message.get("kind").asText());
        assertEquals("", message.get("content").asText());
        assertEquals(KLIPY_GIF, message.at("/media/url").asText());
        assertEquals(KLIPY_PREVIEW, message.at("/media/previewUrl").asText());
        assertEquals(220, message.at("/media/width").asInt());
        assertEquals(180, message.at("/media/height").asInt());

        CommunityMessage saved = communityRepo.findById(message.get("id").asLong()).orElseThrow();
        assertEquals(MessageKind.GIF, saved.getKind());
        assertEquals("abc123", saved.getMedia().getProviderId());
    }

    @Test
    void stickerKeepsItsKind() throws Exception {
        SendChatMessageRequest request = gif(nextClientId(), KLIPY_GIF);
        request.setKind(MessageKind.STICKER);
        JsonNode message = only(capture(() -> controller.sendCommunity(request, as(ALICE))), "/topic/chat.community");
        assertEquals("STICKER", message.get("kind").asText());
    }

    @Test
    void gifsMustComeFromKlipyOverHttps() throws Exception {
        assertRejected(gif(nextClientId(), "http://static.klipy.com/ii/abc/md.gif"), "INVALID_MEDIA");
        assertRejected(gif(nextClientId(), "https://evil.example.com/cat.gif"), "INVALID_MEDIA");
        assertRejected(gif(nextClientId(), "https://klipy.com.evil.io/cat.gif"), "INVALID_MEDIA");
        assertRejected(gif(nextClientId(), "https://notklipy.com/cat.gif"), "INVALID_MEDIA");
        assertRejected(gif(nextClientId(), "javascript:alert(1)"), "INVALID_MEDIA");
        assertRejected(gif(nextClientId(), "https://static.klipy.com/" + "a".repeat(1000)), "INVALID_MEDIA");

        SendChatMessageRequest badPreview = gif(nextClientId(), KLIPY_GIF);
        badPreview.getMedia().setPreviewUrl("https://evil.example.com/preview.gif");
        assertRejected(badPreview, "INVALID_MEDIA");

        SendChatMessageRequest noMedia = gif(nextClientId(), KLIPY_GIF);
        noMedia.setMedia(null);
        assertRejected(noMedia, "INVALID_MEDIA");
    }

    @Test
    void implausibleGifSizesAreDroppedNotRejected() throws Exception {
        SendChatMessageRequest request = gif(nextClientId(), KLIPY_GIF);
        request.getMedia().setWidth(-5);
        request.getMedia().setHeight(99_999);
        JsonNode message = only(capture(() -> controller.sendCommunity(request, as(ALICE))), "/topic/chat.community");
        assertTrue(message.at("/media/width").isNull());
        assertTrue(message.at("/media/height").isNull());
    }

    @Test
    void retryingWithTheSameClientIdDoesNotSaveTwice() throws Exception {
        String clientId = nextClientId();
        long first = only(capture(() -> controller.sendCommunity(text(clientId, "only once"), as(ALICE))),
                "/topic/chat.community").get("id").asLong();
        long second = only(capture(() -> controller.sendCommunity(text(clientId, "only once"), as(ALICE))),
                "/topic/chat.community").get("id").asLong();

        assertEquals(first, second);
        assertEquals(1, communityRepo.findAll().stream()
                .filter(m -> clientId.equals(m.getClientId()) && ALICE.equals(m.getSenderUsername())).count());
    }

    @Test
    void theSameClientIdFromSomeoneElseIsADifferentMessage() throws Exception {
        String clientId = nextClientId();
        long alices = only(capture(() -> controller.sendCommunity(text(clientId, "mine"), as(ALICE))),
                "/topic/chat.community").get("id").asLong();
        long bobs = only(capture(() -> controller.sendCommunity(text(clientId, "mine too"), as(BOB))),
                "/topic/chat.community").get("id").asLong();
        assertTrue(alices != bobs);
    }

    @Test
    void sendsWithoutASignedInUserAreDropped() throws Exception {
        String clientId = nextClientId();
        List<Sent> sent = capture(() -> controller.sendCommunity(text(clientId, "anonymous"), null));
        assertTrue(sent.isEmpty());
        assertTrue(communityRepo.findAll().stream().noneMatch(m -> clientId.equals(m.getClientId())));
    }

    @Test
    void directMessageGoesToBothPeopleOnly() throws Exception {
        String clientId = nextClientId();
        List<Sent> sent = capture(() -> controller.sendDM(dm(clientId, BOB, "hey bob"), as(ALICE)));

        JsonNode toBob = only(sent, "/user/" + BOB + "/queue/messages");
        JsonNode toAlice = only(sent, "/user/" + ALICE + "/queue/messages");
        assertEquals(toBob.get("id"), toAlice.get("id"));
        assertEquals("DM", toBob.get("type").asText());
        assertEquals(BOB, toBob.get("recipientUsername").asText());
        assertEquals(clientId, toBob.get("clientId").asText());
        assertTrue(sent.stream().noneMatch(s -> s.destination().equals("/topic/chat.community")));
    }

    @Test
    void directMessagesNeedARealRecipientOtherThanYourself() throws Exception {
        assertRejected(dm(nextClientId(), ALICE, "talking to myself"), "INVALID_RECIPIENT", true);
        assertRejected(dm(nextClientId(), "chat_nobody_here", "hello?"), "UNKNOWN_RECIPIENT", true);
        assertRejected(dm(nextClientId(), "  ", "hello?"), "UNKNOWN_RECIPIENT", true);
        assertRejected(dm(nextClientId(), null, "hello?"), "UNKNOWN_RECIPIENT", true);
    }

    @Test
    void sendThroughTheRealStompPipelineReportsErrorsToTheSender() throws Exception {
        String clientId = nextClientId();
        JsonNode error = errorFromPipeline(
                objectMapper.writeValueAsBytes(Map.of("clientId", clientId, "content", "c".repeat(501))));
        assertEquals(clientId, error.get("clientId").asText());
        assertEquals("TOO_LONG", error.get("code").asText());
    }

    @Test
    void sendsThatCantBeReadStillReachTheErrorQueueInsteadOfVanishing() throws Exception {
        // Regression: these failed while Spring read the payload, before the handler's own error handling
        String badKind = nextClientId();
        JsonNode error = errorFromPipeline(objectMapper.writeValueAsBytes(Map.of("clientId", badKind, "kind", "BOGUS")));
        assertEquals("INVALID_MESSAGE", error.get("code").asText());
        assertEquals(badKind, error.get("clientId").asText());
        assertFalse(error.get("message").asText().isBlank());

        String badWidth = nextClientId();
        error = errorFromPipeline(objectMapper.writeValueAsBytes(Map.of("clientId", badWidth, "kind", "GIF",
                "media", Map.of("url", KLIPY_GIF, "width", "wide"))));
        assertEquals("INVALID_MESSAGE", error.get("code").asText());
        assertEquals(badWidth, error.get("clientId").asText());

        // Not JSON at all, or no body: there's no clientId to hand back
        error = errorFromPipeline("{not json".getBytes(StandardCharsets.UTF_8));
        assertEquals("INVALID_MESSAGE", error.get("code").asText());
        assertFalse(error.hasNonNull("clientId"));
        assertEquals("INVALID_MESSAGE", errorFromPipeline(new byte[0]).get("code").asText());

        assertTrue(communityRepo.findAll().stream()
                .noneMatch(m -> badKind.equals(m.getClientId()) || badWidth.equals(m.getClientId())));
    }

    @Test
    void theDatabaseRefusesASecondCopyOfAClientIdButNotOfAMissingOne() {
        // Backs the retry check in the service, which two concurrent retries can both get past
        String clientId = nextClientId();
        communityRepo.save(stored(new CommunityMessage(), ALICE, clientId));
        assertThrows(DataIntegrityViolationException.class,
                () -> communityRepo.save(stored(new CommunityMessage(), ALICE, clientId)));
        communityRepo.save(stored(new CommunityMessage(), BOB, clientId));

        DirectMessage dm = stored(new DirectMessage(), ALICE, clientId);
        dm.setRecipientUsername(BOB);
        dmRepo.save(dm);
        DirectMessage again = stored(new DirectMessage(), ALICE, clientId);
        again.setRecipientUsername(CAROL);
        assertThrows(DataIntegrityViolationException.class, () -> dmRepo.save(again));

        // Older apps send no clientId at all, as often as they like
        communityRepo.save(stored(new CommunityMessage(), ALICE, null));
        communityRepo.save(stored(new CommunityMessage(), ALICE, null));
        for (int i = 0; i < 2; i++) {
            DirectMessage legacy = stored(new DirectMessage(), ALICE, null);
            legacy.setRecipientUsername(BOB);
            dmRepo.save(legacy);
        }
    }

    // ── History ────────────────────────────────────────────────────────────

    @Test
    void communityHistoryReturnsTheNewestPageOldestFirst() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) ids.add(sendCommunityText(ALICE, "page message " + i));

        assertEquals(ids.subList(2, 5), idsOf(getJson(aliceToken, HISTORY + "?limit=3")));

        List<Long> older = idsOf(getJson(aliceToken, HISTORY + "?limit=3&before=" + ids.get(2)));
        assertEquals(ids.subList(0, 2), older.subList(older.size() - 2, older.size()));
        assertTrue(older.stream().allMatch(id -> id < ids.get(2)));
    }

    @Test
    void communityHistoryShowsTheLatestMessagesOnceThereAreMoreThanFifty() throws Exception {
        // Regression: history used to return the oldest 50, so new messages never loaded
        long last = 0;
        for (int i = 0; i < 55; i++) last = sendCommunityText(BOB, "filler " + i);
        List<Long> page = idsOf(getJson(bobToken, HISTORY));
        assertEquals(50, page.size());
        assertEquals(last, page.get(page.size() - 1));
    }

    @Test
    void historyPageSizeIsClamped() throws Exception {
        sendCommunityText(ALICE, "one");
        sendCommunityText(ALICE, "two");
        assertEquals(1, getJson(aliceToken, HISTORY + "?limit=0").size());
        assertEquals(1, getJson(aliceToken, HISTORY + "?limit=1").size());
        assertTrue(getJson(aliceToken, HISTORY + "?limit=5000").size() <= 100);
    }

    @Test
    void directHistoryOnlyHasThatConversationAndPages() throws Exception {
        long first = sendDmText(ALICE, BOB, "dm one");
        long second = sendDmText(BOB, ALICE, "dm two");
        long third = sendDmText(ALICE, BOB, "dm three");
        sendDmText(ALICE, CAROL, "not for bob");

        List<Long> page = idsOf(getJson(aliceToken, "/api/messaging/dm/" + BOB + "?limit=2"));
        assertEquals(List.of(second, third), page);
        List<Long> older = idsOf(getJson(aliceToken, "/api/messaging/dm/" + BOB + "?limit=2&before=" + second));
        assertEquals(first, older.get(older.size() - 1));

        JsonNode carolsView = getJson(carolToken, "/api/messaging/dm/" + BOB);
        assertTrue(idsOf(carolsView).stream().noneMatch(id -> id == first || id == second || id == third));
    }

    @Test
    void inboxShowsLabelsForGifsAndDeletedMessages() throws Exception {
        String dave = "chat_dave";
        String daveToken = tokenFor(dave, "USER");
        String erin = "chat_erin";
        tokenFor(erin, "USER");

        only(capture(() -> controller.sendDM(dmGif(nextClientId(), dave, KLIPY_GIF), as(ALICE))),
                "/user/" + dave + "/queue/messages");
        JsonNode inbox = getJson(daveToken, "/api/messaging/dm/conversations");
        JsonNode withAlice = conversationWith(inbox, ALICE);
        assertEquals("GIF", withAlice.get("lastMessage").asText());
        assertEquals("GIF", withAlice.get("lastKind").asText());
        assertTrue(withAlice.get("unread").asBoolean());

        long toErin = sendDmText(dave, erin, "oops");
        deleteMessageAs(daveToken, "dm", toErin).andExpect(status().isOk());
        assertEquals("Message deleted",
                conversationWith(getJson(daveToken, "/api/messaging/dm/conversations"), erin).get("lastMessage").asText());
    }

    // ── Reactions ──────────────────────────────────────────────────────────

    @Test
    void reactionsCanBeSetSwitchedAndRemovedOnePerMember() throws Exception {
        long id = sendCommunityText(ALICE, "react to me");

        JsonNode afterBob = json(putReactionAs(bobToken, "community", id, "🔥").andExpect(status().isOk()));
        assertEquals(List.of(BOB), usernames(afterBob, "🔥"));

        putReactionAs(carolToken, "community", id, "🔥").andExpect(status().isOk());
        // Bob switches; he can only have one reaction
        List<Sent> sent = capture(() -> putReactionAs(bobToken, "community", id, "💪").andExpect(status().isOk()));
        JsonNode broadcast = only(sent, "/topic/chat.community");
        assertEquals(List.of(CAROL), usernames(broadcast, "🔥"));
        assertEquals(List.of(BOB), usernames(broadcast, "💪"));

        JsonNode afterRemove = json(deleteReactionAs(bobToken, "community", id).andExpect(status().isOk()));
        assertFalse(afterRemove.get("reactions").has("💪"));
        assertEquals(List.of(CAROL), usernames(afterRemove, "🔥"));

        // Removing again is harmless
        deleteReactionAs(bobToken, "community", id).andExpect(status().isOk());

        // History carries reactions too
        JsonNode inHistory = findById(getJson(aliceToken, HISTORY), id);
        assertEquals(List.of(CAROL), usernames(inHistory, "🔥"));
    }

    @Test
    void reactingWithTheSameEmojiAgainKeepsOneReaction() throws Exception {
        long id = sendCommunityText(ALICE, "double tap");
        putReactionAs(bobToken, "community", id, "👍").andExpect(status().isOk());
        JsonNode again = json(putReactionAs(bobToken, "community", id, "👍").andExpect(status().isOk()));
        assertEquals(List.of(BOB), usernames(again, "👍"));
    }

    @Test
    void onlyTheQuickReactionsAreAllowed() throws Exception {
        long id = sendCommunityText(ALICE, "pick one");
        putReactionAs(bobToken, "community", id, "🍕").andExpect(status().isBadRequest());
        putReactionAs(bobToken, "community", id, "lol").andExpect(status().isBadRequest());
        putJsonAs(bobToken, reactionUrl("community", id), Map.of()).andExpect(status().isBadRequest());

        // A heart without the emoji variation selector counts as ❤️
        JsonNode heart = json(putReactionAs(bobToken, "community", id, "❤").andExpect(status().isOk()));
        assertEquals(List.of(BOB), usernames(heart, "❤️"));
    }

    @Test
    void onlyConversationMembersCanReactToADirectMessage() throws Exception {
        long id = sendDmText(ALICE, BOB, "just us");
        putReactionAs(carolToken, "dm", id, "😂").andExpect(status().isNotFound());
        deleteReactionAs(carolToken, "dm", id).andExpect(status().isNotFound());

        List<Sent> sent = capture(() -> putReactionAs(bobToken, "dm", id, "😂").andExpect(status().isOk()));
        assertEquals(List.of(BOB), usernames(only(sent, "/user/" + ALICE + "/queue/messages"), "😂"));
        only(sent, "/user/" + BOB + "/queue/messages");
    }

    @Test
    void unknownChannelsAndMessagesAre404AndSigningInIsRequired() throws Exception {
        long id = sendCommunityText(ALICE, "exists");
        putReactionAs(bobToken, "groups", id, "👍").andExpect(status().isNotFound());
        putReactionAs(bobToken, "community", 987_654_321L, "👍").andExpect(status().isNotFound());
        putReactionAs(bobToken, "dm", 987_654_321L, "👍").andExpect(status().isNotFound());
        putJsonAs(null, reactionUrl("community", id), Map.of("emoji", "👍")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/messaging/community/messages/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get(HISTORY)).andExpect(status().isUnauthorized());
    }

    // ── Deleting ───────────────────────────────────────────────────────────

    @Test
    void sendersCanDeleteTheirOwnMessagesWhichStayAsPlaceholders() throws Exception {
        long id = sendCommunityText(ALICE, "regret this");
        putReactionAs(bobToken, "community", id, "😮").andExpect(status().isOk());

        deleteMessageAs(bobToken, "community", id).andExpect(status().isForbidden());

        List<Sent> sent = capture(() -> deleteMessageAs(aliceToken, "community", id).andExpect(status().isOk()));
        JsonNode broadcast = only(sent, "/topic/chat.community");
        assertTrue(broadcast.get("deleted").asBoolean());
        assertEquals("", broadcast.get("content").asText());
        assertTrue(broadcast.get("reactions").isEmpty());

        JsonNode inHistory = findById(getJson(bobToken, HISTORY), id);
        assertTrue(inHistory.get("deleted").asBoolean());
        assertEquals("", inHistory.get("content").asText());

        // The row is kept for moderation
        assertEquals("regret this", communityRepo.findById(id).orElseThrow().getContent());

        deleteMessageAs(aliceToken, "community", id).andExpect(status().isOk());
        putReactionAs(bobToken, "community", id, "👍").andExpect(status().isConflict());
        reportAs(bobToken, "community", id, Map.of("reason", "SPAM")).andExpect(status().isConflict());
    }

    @Test
    void deletedGifsLoseTheirMediaForMembers() throws Exception {
        long id = only(capture(() -> controller.sendCommunity(gif(nextClientId(), KLIPY_GIF), as(ALICE))),
                "/topic/chat.community").get("id").asLong();
        JsonNode deleted = json(deleteMessageAs(aliceToken, "community", id).andExpect(status().isOk()));
        assertTrue(deleted.get("media").isNull());
    }

    @Test
    void outsidersCantDeleteADirectMessage() throws Exception {
        long id = sendDmText(ALICE, BOB, "private");
        deleteMessageAs(carolToken, "dm", id).andExpect(status().isNotFound());
        deleteMessageAs(bobToken, "dm", id).andExpect(status().isForbidden());
        List<Sent> sent = capture(() -> deleteMessageAs(aliceToken, "dm", id).andExpect(status().isOk()));
        assertTrue(only(sent, "/user/" + BOB + "/queue/messages").get("deleted").asBoolean());
    }

    // ── Reporting ──────────────────────────────────────────────────────────

    @Test
    void membersCanReportSomeoneElsesMessageOnce() throws Exception {
        long id = sendCommunityText(ALICE, "rude words");

        JsonNode report = json(reportAs(bobToken, "community", id, Map.of("reason", "HARASSMENT", "note", "  not ok  "))
                .andExpect(status().isCreated()));
        assertEquals("OPEN", report.get("status").asText());
        assertEquals("HARASSMENT", report.get("reason").asText());
        assertEquals("not ok", report.get("note").asText());

        reportAs(bobToken, "community", id, Map.of("reason", "SPAM")).andExpect(status().isConflict());
        reportAs(aliceToken, "community", id, Map.of("reason", "SPAM")).andExpect(status().isBadRequest());
        reportAs(carolToken, "community", id, Map.of()).andExpect(status().isBadRequest());
        reportAs(carolToken, "community", id, Map.of("reason", "NOT_A_REASON")).andExpect(status().isBadRequest());
        reportAs(carolToken, "community", id, Map.of("reason", "OTHER", "note", "n".repeat(301)))
                .andExpect(status().isBadRequest());
        reportAs(carolToken, "community", id, Map.of("reason", "OTHER", "note", "   "))
                .andExpect(status().isCreated());
        assertEquals(2, reportRepo.findByChannelAndMessageIdOrderByReportedAtDesc(ChatChannel.COMMUNITY, id).size());
    }

    @Test
    void onlyConversationMembersCanReportADirectMessage() throws Exception {
        long id = sendDmText(ALICE, BOB, "dm to report");
        reportAs(carolToken, "dm", id, Map.of("reason", "SPAM")).andExpect(status().isNotFound());
        reportAs(bobToken, "dm", id, Map.of("reason", "SPAM")).andExpect(status().isCreated());
    }

    // ── Admin report queue ─────────────────────────────────────────────────

    @Test
    void adminEndpointsAreForAdminsOnly() throws Exception {
        getAs(aliceToken, CHAT_REPORTS).andExpect(status().isForbidden());
        getAs(aliceToken, CHAT_REPORTS + "/summary").andExpect(status().isForbidden());
        putJsonAs(aliceToken, CHAT_REPORTS + "/community/1/dismiss", Map.of()).andExpect(status().isForbidden());
        putJsonAs(aliceToken, CHAT_REPORTS + "/community/1/remove", Map.of()).andExpect(status().isForbidden());
        mvc.perform(get(CHAT_REPORTS)).andExpect(status().isUnauthorized());

        getAs(tokenFor("chat_admin_prefixed", "ROLE_ADMIN"), CHAT_REPORTS + "/summary").andExpect(status().isOk());
    }

    @Test
    void reportsAreGroupedByMessageAndShowWhatWasSaidEvenAfterDeleting() throws Exception {
        long id = sendCommunityText(ALICE, "report me twice");
        JsonNode before = getJson(adminToken, CHAT_REPORTS + "/summary");

        reportAs(bobToken, "community", id, Map.of("reason", "SPAM")).andExpect(status().isCreated());
        reportAs(carolToken, "community", id, Map.of("reason", "INAPPROPRIATE", "note", "gross")).andExpect(status().isCreated());

        JsonNode after = getJson(adminToken, CHAT_REPORTS + "/summary");
        assertEquals(before.get("openMessages").asLong() + 1, after.get("openMessages").asLong());
        assertEquals(before.get("openReports").asLong() + 2, after.get("openReports").asLong());

        deleteMessageAs(aliceToken, "community", id).andExpect(status().isOk());

        JsonNode group = groupFor(getJson(adminToken, CHAT_REPORTS), "COMMUNITY", id);
        assertEquals(ALICE, group.get("senderUsername").asText());
        assertEquals("report me twice", group.get("content").asText());
        assertTrue(group.get("deletedBySender").asBoolean());
        assertTrue(group.get("messageAvailable").asBoolean());
        assertEquals(2, group.get("openCount").asInt());
        assertEquals(2, group.get("reports").size());
        // Newest report first
        assertEquals(CAROL, group.at("/reports/0/reporterUsername").asText());
        assertEquals("gross", group.at("/reports/0/note").asText());
    }

    @Test
    void reportedDirectMessagesShowBothPeople() throws Exception {
        long id = only(capture(() -> controller.sendDM(dmGif(nextClientId(), BOB, KLIPY_GIF), as(ALICE))),
                "/user/" + BOB + "/queue/messages").get("id").asLong();
        reportAs(bobToken, "dm", id, Map.of("reason", "OTHER")).andExpect(status().isCreated());

        JsonNode group = groupFor(getJson(adminToken, CHAT_REPORTS), "DM", id);
        assertEquals(ALICE, group.get("senderUsername").asText());
        assertEquals(BOB, group.get("recipientUsername").asText());
        assertEquals("GIF", group.get("kind").asText());
        assertEquals(KLIPY_GIF, group.at("/media/url").asText());
    }

    @Test
    void dismissingClosesTheReportsAndLeavesTheMessageUp() throws Exception {
        long id = sendCommunityText(ALICE, "harmless joke");
        reportAs(bobToken, "community", id, Map.of("reason", "OTHER")).andExpect(status().isCreated());

        JsonNode group = json(putJsonAs(adminToken, CHAT_REPORTS + "/community/" + id + "/dismiss", Map.of())
                .andExpect(status().isOk()));
        assertEquals(0, group.get("openCount").asInt());
        assertEquals("DISMISSED", group.at("/reports/0/status").asText());
        assertEquals("chat_admin", group.at("/reports/0/resolvedBy").asText());
        assertFalse(group.get("removedByStaff").asBoolean());

        putJsonAs(adminToken, CHAT_REPORTS + "/community/" + id + "/dismiss", Map.of()).andExpect(status().isConflict());
        assertTrue(findGroup(getJson(adminToken, CHAT_REPORTS), "COMMUNITY", id) == null);
        assertNotNull(findGroup(getJson(adminToken, CHAT_REPORTS + "?includeResolved=true"), "COMMUNITY", id));
        assertFalse(findById(getJson(aliceToken, HISTORY), id).get("removedByStaff").asBoolean());
    }

    @Test
    void removingHidesTheMessageLiveAndActionsTheReports() throws Exception {
        long id = sendCommunityText(ALICE, "really bad");
        reportAs(bobToken, "community", id, Map.of("reason", "HARASSMENT")).andExpect(status().isCreated());

        List<Sent> sent = capture(() -> putJsonAs(adminToken, CHAT_REPORTS + "/community/" + id + "/remove", Map.of())
                .andExpect(status().isOk()));
        JsonNode broadcast = only(sent, "/topic/chat.community");
        assertTrue(broadcast.get("removedByStaff").asBoolean());
        assertEquals("", broadcast.get("content").asText());

        JsonNode inHistory = findById(getJson(carolToken, HISTORY), id);
        assertTrue(inHistory.get("removedByStaff").asBoolean());
        assertEquals("", inHistory.get("content").asText());

        assertTrue(reportRepo.findByChannelAndMessageIdOrderByReportedAtDesc(ChatChannel.COMMUNITY, id).stream()
                .allMatch(r -> r.getStatus() == ReportStatus.ACTIONED));
        putJsonAs(adminToken, CHAT_REPORTS + "/community/" + id + "/remove", Map.of()).andExpect(status().isConflict());
        putReactionAs(bobToken, "community", id, "👍").andExpect(status().isConflict());
    }

    @Test
    void aStaleCopyOfAReportCantOverwriteAnotherStaffMembersDecision() throws Exception {
        long id = sendCommunityText(ALICE, "two admins at once");
        long reportId = json(reportAs(bobToken, "community", id, Map.of("reason", "SPAM"))
                .andExpect(status().isCreated())).get("id").asLong();
        // What each admin's request read before either saved
        ChatMessageReport seenByFirst = reportRepo.findById(reportId).orElseThrow();
        ChatMessageReport seenBySecond = reportRepo.findById(reportId).orElseThrow();

        seenByFirst.resolve(ReportStatus.DISMISSED, "staff_one");
        reportRepo.saveAndFlush(seenByFirst);
        seenBySecond.resolve(ReportStatus.ACTIONED, "staff_two");
        assertThrows(OptimisticLockingFailureException.class, () -> reportRepo.saveAndFlush(seenBySecond));

        ChatMessageReport stored = reportRepo.findById(reportId).orElseThrow();
        assertEquals(ReportStatus.DISMISSED, stored.getStatus());
        assertEquals("staff_one", stored.getResolvedBy());
    }

    @Test
    void rowsWrittenByANewerDeployDontBreakHistoryOrTheReportQueue() throws Exception {
        long photo = sendCommunityText(ALICE, "pretend this is a photo");
        long reported = sendCommunityText(ALICE, "reported under a newer build");
        reportAs(bobToken, "community", reported, Map.of("reason", "SPAM")).andExpect(status().isCreated());
        reportAs(carolToken, "community", reported, Map.of("reason", "OTHER")).andExpect(status().isCreated());
        long ordinary = sendCommunityText(ALICE, "reported normally");
        reportAs(bobToken, "community", ordinary, Map.of("reason", "SPAM")).andExpect(status().isCreated());

        // Values this build has never heard of
        jdbc.update("UPDATE community_messages SET kind = 'PHOTO' WHERE id = ?", photo);
        jdbc.update("UPDATE chat_message_reports SET reason = 'SCAM', status = 'ESCALATED'"
                + " WHERE message_id = ? AND reporter_username = ?", reported, BOB);
        jdbc.update("UPDATE chat_message_reports SET channel = 'GROUP'"
                + " WHERE message_id = ? AND reporter_username = ?", reported, CAROL);
        try {
            assertEquals("TEXT", findById(getJson(aliceToken, HISTORY), photo).get("kind").asText());

            JsonNode all = getJson(adminToken, CHAT_REPORTS + "?includeResolved=true");
            assertNotNull(findGroup(all, "COMMUNITY", ordinary));
            JsonNode group = groupFor(all, "COMMUNITY", reported);
            // Carol's report is on a channel this build can't act on, so it's left out
            assertEquals(1, group.get("reports").size());
            assertEquals(BOB, group.at("/reports/0/reporterUsername").asText());
            assertEquals("OTHER", group.at("/reports/0/reason").asText());

            getAs(adminToken, CHAT_REPORTS + "/summary").andExpect(status().isOk());
        } finally {
            jdbc.update("DELETE FROM chat_message_reports WHERE message_id = ?", reported);
            jdbc.update("UPDATE community_messages SET kind = 'TEXT' WHERE id = ?", photo);
        }
    }

    @Test
    void onlyReportedMessagesCanBeModerated() throws Exception {
        long id = sendCommunityText(ALICE, "never reported");
        putJsonAs(adminToken, CHAT_REPORTS + "/community/" + id + "/remove", Map.of()).andExpect(status().isNotFound());
        putJsonAs(adminToken, CHAT_REPORTS + "/community/" + id + "/dismiss", Map.of()).andExpect(status().isNotFound());
        putJsonAs(adminToken, CHAT_REPORTS + "/groups/" + id + "/remove", Map.of()).andExpect(status().isNotFound());
    }

    // ── GIF search proxy ───────────────────────────────────────────────────

    @Test
    void gifSearchAnswers503UntilAKeyIsConfigured() throws Exception {
        getAs(aliceToken, "/api/media/gifs/trending").andExpect(status().isServiceUnavailable());
        getAs(aliceToken, "/api/media/stickers/search?q=gym").andExpect(status().isServiceUnavailable());
        getAs(aliceToken, "/api/media/clips/trending").andExpect(status().isNotFound());
        mvc.perform(get("/api/media/gifs/trending")).andExpect(status().isUnauthorized());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private record Sent(String destination, JsonNode body) {}

    private interface Action {
        void run() throws Exception;
    }

    private String nextClientId() {
        return "it-" + System.nanoTime() + "-" + (++clientIds);
    }

    private static Principal as(String username) {
        return () -> username;
    }

    private static SendChatMessageRequest text(String clientId, String content) {
        SendChatMessageRequest request = new SendChatMessageRequest();
        request.setClientId(clientId);
        request.setContent(content);
        request.setKind(MessageKind.TEXT);
        return request;
    }

    private static SendChatMessageRequest gif(String clientId, String url) {
        SendChatMessageRequest request = new SendChatMessageRequest();
        request.setClientId(clientId);
        request.setKind(MessageKind.GIF);
        request.setMedia(new SendChatMessageRequest.MediaPayload(url, KLIPY_PREVIEW, 220, 180, "abc123"));
        return request;
    }

    private static SendChatMessageRequest dm(String clientId, String recipient, String content) {
        SendChatMessageRequest request = text(clientId, content);
        request.setRecipient(recipient);
        return request;
    }

    private static SendChatMessageRequest dmGif(String clientId, String recipient, String url) {
        SendChatMessageRequest request = gif(clientId, url);
        request.setRecipient(recipient);
        return request;
    }

    private long sendCommunityText(String sender, String content) throws Exception {
        return only(capture(() -> controller.sendCommunity(text(nextClientId(), content), as(sender))),
                "/topic/chat.community").get("id").asLong();
    }

    private long sendDmText(String sender, String recipient, String content) throws Exception {
        return only(capture(() -> controller.sendDM(dm(nextClientId(), recipient, content), as(sender))),
                "/user/" + recipient + "/queue/messages").get("id").asLong();
    }

    private void assertRejected(SendChatMessageRequest request, String code) throws Exception {
        assertRejected(request, code, false);
    }

    private void assertRejected(SendChatMessageRequest request, String code, boolean direct) throws Exception {
        List<Sent> sent = capture(() -> {
            if (direct) controller.sendDM(request, as(ALICE));
            else controller.sendCommunity(request, as(ALICE));
        });
        JsonNode error = only(sent, "/user/" + ALICE + "/queue/errors");
        assertEquals(code, error.get("code").asText(), "error for " + request.getClientId());
        assertFalse(error.get("message").asText().isBlank());
        if (request.getClientId() != null && !request.getClientId().contains(" ")) {
            assertEquals(request.getClientId(), error.get("clientId").asText());
        }
        assertEquals(1, sent.size(), "nothing but the error is sent");
    }

    /** A message saved straight through the repository, skipping the service's checks. */
    private static <M extends ChatMessageBase> M stored(M message, String sender, String clientId) {
        message.setSenderUsername(sender);
        message.setContent("stored directly");
        message.setClientId(clientId);
        return message;
    }

    /** Sends a raw community frame through the real inbound channel as Alice; returns what reached her error queue. */
    private JsonNode errorFromPipeline(byte[] payload) throws Exception {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination("/app/chat.community.send");
        headers.setSessionId("chat-it-session");
        // A real WebSocket session always carries these; the annotation handler requires them
        headers.setSessionAttributes(new java.util.HashMap<>());
        headers.setUser(as(ALICE));
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        Message<byte[]> frame = MessageBuilder.createMessage(payload, headers.getMessageHeaders());

        List<Sent> sent = new CopyOnWriteArrayList<>();
        ChannelInterceptor capture = captureInto(sent);
        brokerChannel.addInterceptor(capture);
        try {
            clientInboundChannel.send(frame);
            // The inbound channel runs handlers on a thread pool
            long deadline = System.currentTimeMillis() + 5000;
            while (sent.stream().noneMatch(s -> s.destination().endsWith("/queue/errors"))
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
        } finally {
            brokerChannel.removeInterceptor(capture);
        }
        return only(sent, "/user/" + ALICE + "/queue/errors");
    }

    private ChannelInterceptor captureInto(List<Sent> sent) {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
                if (destination != null
                        && (destination.equals("/topic/chat.community") || destination.startsWith("/user/"))) {
                    try {
                        sent.add(new Sent(destination, objectMapper.readTree((byte[]) message.getPayload())));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                return message;
            }
        };
    }

    /** Runs the action and returns the chat messages it sent to the broker. */
    private List<Sent> capture(Action action) throws Exception {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        ChannelInterceptor capture = captureInto(sent);
        brokerChannel.addInterceptor(capture);
        try {
            action.run();
        } finally {
            brokerChannel.removeInterceptor(capture);
        }
        return sent;
    }

    private static JsonNode only(List<Sent> sent, String destination) {
        List<JsonNode> matching = sent.stream().filter(s -> s.destination().equals(destination)).map(Sent::body).toList();
        if (matching.size() != 1) {
            fail("expected one message to " + destination + " but got " + matching.size() + " in "
                    + sent.stream().map(Sent::destination).toList());
        }
        return matching.get(0);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode getJson(String token, String url) throws Exception {
        return json(getAs(token, url).andExpect(status().isOk()));
    }

    private static List<Long> idsOf(JsonNode messages) {
        List<Long> ids = new ArrayList<>();
        messages.forEach(m -> ids.add(m.get("id").asLong()));
        return ids;
    }

    private static JsonNode findById(JsonNode messages, long id) {
        for (JsonNode m : messages) {
            if (m.get("id").asLong() == id) return m;
        }
        fail("message " + id + " not in history");
        return null;
    }

    private static JsonNode conversationWith(JsonNode inbox, String partner) {
        for (JsonNode c : inbox) {
            if (c.get("partnerUsername").asText().equals(partner)) return c;
        }
        fail("no conversation with " + partner);
        return null;
    }

    private static List<String> usernames(JsonNode message, String emoji) {
        List<String> names = new ArrayList<>();
        message.get("reactions").path(emoji).forEach(n -> names.add(n.asText()));
        return names;
    }

    private static JsonNode findGroup(JsonNode groups, String channel, long messageId) {
        for (JsonNode g : groups) {
            if (g.get("channel").asText().equals(channel) && g.get("messageId").asLong() == messageId) return g;
        }
        return null;
    }

    private static JsonNode groupFor(JsonNode groups, String channel, long messageId) {
        JsonNode group = findGroup(groups, channel, messageId);
        if (group == null) fail("no report group for " + channel + " " + messageId);
        return group;
    }

    private static String reactionUrl(String channel, long id) {
        return "/api/messaging/" + channel + "/messages/" + id + "/reaction";
    }

    private ResultActions putReactionAs(String token, String channel, long id, String emoji) throws Exception {
        return putJsonAs(token, reactionUrl(channel, id), Map.of("emoji", emoji));
    }

    private ResultActions deleteReactionAs(String token, String channel, long id) throws Exception {
        return mvc.perform(delete(reactionUrl(channel, id)).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions deleteMessageAs(String token, String channel, long id) throws Exception {
        return mvc.perform(delete("/api/messaging/" + channel + "/messages/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions reportAs(String token, String channel, long id, Object body) throws Exception {
        return mvc.perform(json(post("/api/messaging/" + channel + "/messages/" + id + "/report")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)), body));
    }
}
