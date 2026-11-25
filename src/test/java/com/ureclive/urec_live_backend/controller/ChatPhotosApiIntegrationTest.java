package com.ureclive.urec_live_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ureclive.urec_live_backend.dto.SendChatMessageRequest;
import com.ureclive.urec_live_backend.entity.ChatMedia;
import com.ureclive.urec_live_backend.entity.CommunityMessage;
import com.ureclive.urec_live_backend.entity.MessageKind;
import com.ureclive.urec_live_backend.repository.ChatMediaRepository;
import com.ureclive.urec_live_backend.repository.CommunityMessageRepository;
import com.ureclive.urec_live_backend.service.ChatMediaService;
import com.ureclive.urec_live_backend.service.LocalDiskMediaStorage;
import com.ureclive.urec_live_backend.service.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ureclive.urec_live_backend.service.ChatPhotoFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat photos end to end on local storage: upload (checks and EXIF stripping), sending an IMAGE
 * message over branch 1's send path, the signed URL members get and what it serves, DMs, history,
 * the inbox, deleting, and the admin report queue.
 */
class ChatPhotosApiIntegrationTest extends ApiIntegrationTestSupport {

    private static final String UPLOAD = "/api/messaging/media";
    private static final String TOPIC = "/topic/chat.community";
    private static final String ALICE = "photo_alice";
    private static final String BOB = "photo_bob";
    private static final String CAROL = "photo_carol";

    @Autowired private ChatMessagingController controller;
    @Autowired @Qualifier("brokerChannel") private AbstractSubscribableChannel brokerChannel;
    @Autowired private ChatMediaRepository mediaRepo;
    @Autowired private CommunityMessageRepository communityRepo;
    @Autowired private MediaStorage storage;

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
        adminToken = tokenFor("photo_admin", "ADMIN");
    }

    // ── Uploading ──────────────────────────────────────────────────────────

    @Test
    void anUploadIsStoredWithoutItsExifAndGpsData() throws Exception {
        byte[] photo = withExif(jpeg(320, 240, Color.MAGENTA), 6);
        JsonNode body = json(upload(aliceToken, photo).andExpect(status().isCreated()));

        long mediaId = body.get("mediaId").asLong();
        // Orientation 6 was applied, so the stored copy is upright: 240 wide, 320 tall
        assertEquals(240, body.get("width").asInt());
        assertEquals(320, body.get("height").asInt());

        ChatMedia saved = mediaRepo.findById(mediaId).orElseThrow();
        assertEquals(ALICE, saved.getUploaderUsername());
        assertEquals("image/jpeg", saved.getContentType());
        assertFalse(saved.isAttached());
        byte[] stored = ((LocalDiskMediaStorage) storage).read(saved.getStorageKey()).orElseThrow();
        assertEquals(saved.getSizeBytes(), stored.length);
        assertFalse(hasExif(stored));
        assertFalse(contains(stored, GPS_MARKER));
    }

    @Test
    void badUploadsAreRefusedAndNothingIsStored() throws Exception {
        long before = mediaRepo.count();

        upload(aliceToken, png(20, 20)).andExpect(status().isUnsupportedMediaType());
        byte[] tooBig = new byte[(int) ChatMediaService.MAX_UPLOAD_BYTES + 1];
        System.arraycopy(jpeg(8, 8, Color.RED), 0, tooBig, 0, 3);
        upload(aliceToken, tooBig).andExpect(status().isPayloadTooLarge());
        byte[] corrupt = new byte[300];
        corrupt[0] = (byte) 0xFF;
        corrupt[1] = (byte) 0xD8;
        corrupt[2] = (byte) 0xFF;
        upload(aliceToken, corrupt).andExpect(status().isBadRequest());
        upload(aliceToken, new byte[0]).andExpect(status().isBadRequest());
        mvc.perform(multipart(UPLOAD).header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(UPLOAD).file(new MockMultipartFile("file", "p.jpg", "image/jpeg", jpeg(8, 8, Color.RED))))
                .andExpect(status().isUnauthorized());

        assertEquals(before, mediaRepo.count());
    }

    // ── Sending ────────────────────────────────────────────────────────────

    @Test
    void aPhotoMessageCarriesASignedUrlThatServesThePhoto() throws Exception {
        long mediaId = uploadPhoto(aliceToken, jpeg(200, 100, Color.BLUE));
        String clientId = nextClientId();

        JsonNode message = only(capture(() -> controller.sendCommunity(photo(clientId, mediaId, "  leg day  "), as(ALICE))), TOPIC);

        assertEquals("IMAGE", message.get("kind").asText());
        assertEquals("leg day", message.get("content").asText());
        assertEquals(clientId, message.get("clientId").asText());
        assertEquals(mediaId, message.at("/media/mediaId").asLong());
        assertEquals(200, message.at("/media/width").asInt());
        assertEquals(100, message.at("/media/height").asInt());
        String url = message.at("/media/url").asText();
        assertTrue(url.matches("/api/messaging/media/" + mediaId + "/raw\\?exp=\\d+&sig=[A-Za-z0-9_-]+"), url);
        assertTrue(mediaRepo.findById(mediaId).orElseThrow().isAttached());

        // Image loaders don't send the sign-in header; the signature is the permission
        MvcResult photo = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertTrue(photo.getResponse().getHeader(HttpHeaders.CACHE_CONTROL).contains("private"));
        byte[] served = photo.getResponse().getContentAsByteArray();
        assertArrayEquals(((LocalDiskMediaStorage) storage).read(mediaRepo.findById(mediaId).orElseThrow().getStorageKey()).orElseThrow(), served);

        CommunityMessage saved = communityRepo.findById(message.get("id").asLong()).orElseThrow();
        assertEquals(MessageKind.IMAGE, saved.getKind());
        assertEquals(mediaId, saved.getMedia().getUploadId());
        assertNull(saved.getMedia().getUrl(), "the expiring URL isn't stored");
    }

    @Test
    void photoUrlsOnlyWorkWithTheirOwnSignature() throws Exception {
        long mediaId = uploadPhoto(aliceToken, jpeg(20, 20, Color.BLUE));
        String url = only(capture(() -> controller.sendCommunity(photo(nextClientId(), mediaId, null), as(ALICE))), TOPIC)
                .at("/media/url").asText();
        String exp = url.replaceAll(".*exp=(\\d+).*", "$1");
        String sig = url.replaceAll(".*sig=", "");

        String changed = (sig.charAt(0) == 'A' ? "B" : "A") + sig.substring(1);
        mvc.perform(get(url.replace("sig=" + sig, "sig=" + changed))).andExpect(status().isForbidden());
        mvc.perform(get(url.replace("exp=" + exp, "exp=" + (Long.parseLong(exp) + 1)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/messaging/media/" + (mediaId + 1000) + "/raw?exp=" + exp + "&sig=" + sig)).andExpect(status().isForbidden());
        mvc.perform(get("/api/messaging/media/" + mediaId + "/raw")).andExpect(status().isForbidden());
        // An expired one: signed for a second that has passed
        mvc.perform(get("/api/messaging/media/" + mediaId + "/raw?exp=1&sig=" + sig)).andExpect(status().isForbidden());
    }

    @Test
    void theCaptionIsOptionalButCapped() throws Exception {
        long mediaId = uploadPhoto(aliceToken, jpeg(20, 20, Color.BLUE));
        assertRejected(photo(nextClientId(), mediaId, "x".repeat(501)), ALICE, "TOO_LONG");
        assertFalse(mediaRepo.findById(mediaId).orElseThrow().isAttached(), "a refused send leaves the photo free");

        JsonNode message = only(capture(() -> controller.sendCommunity(photo(nextClientId(), mediaId, null), as(ALICE))), TOPIC);
        assertEquals("", message.get("content").asText());
        assertEquals("IMAGE", message.get("kind").asText());
    }

    @Test
    void aPhotoCanOnlyBeSentByItsUploaderAndOnlyOnce() throws Exception {
        long mediaId = uploadPhoto(aliceToken, jpeg(20, 20, Color.BLUE));

        assertRejected(photo(nextClientId(), mediaId, "mine now"), BOB, "INVALID_MEDIA");
        assertRejected(photo(nextClientId(), 987_654_321L, "hi"), ALICE, "INVALID_MEDIA");
        assertRejected(photo(nextClientId(), null, "hi"), ALICE, "INVALID_MEDIA");
        SendChatMessageRequest noMedia = photo(nextClientId(), mediaId, "hi");
        noMedia.setMedia(null);
        assertRejected(noMedia, ALICE, "INVALID_MEDIA");
        assertFalse(mediaRepo.findById(mediaId).orElseThrow().isAttached());

        only(capture(() -> controller.sendCommunity(photo(nextClientId(), mediaId, "first"), as(ALICE))), TOPIC);
        assertRejected(photo(nextClientId(), mediaId, "again"), ALICE, "MEDIA_IN_USE");
    }

    @Test
    void retryingAPhotoSendWithItsClientIdSendsTheSavedMessageAgain() throws Exception {
        long mediaId = uploadPhoto(aliceToken, jpeg(20, 20, Color.BLUE));
        String clientId = nextClientId();

        long first = only(capture(() -> controller.sendCommunity(photo(clientId, mediaId, "once"), as(ALICE))), TOPIC)
                .get("id").asLong();
        // The echo got lost and the app retries: the photo is attached by now, but that's this message
        List<Sent> retry = capture(() -> controller.sendCommunity(photo(clientId, mediaId, "once"), as(ALICE)));

        assertEquals(first, only(retry, TOPIC).get("id").asLong());
        assertTrue(retry.stream().noneMatch(s -> s.destination().endsWith("/queue/errors")), "no error for the retry");
        assertEquals(1, communityRepo.findAll().stream()
                .filter(m -> clientId.equals(m.getClientId()) && ALICE.equals(m.getSenderUsername())).count());
    }

    // ── DMs, history and the inbox ─────────────────────────────────────────

    @Test
    void aDirectPhotoReachesBothPeopleAndTheirHistoryButNoOneElse() throws Exception {
        long mediaId = uploadPhoto(aliceToken, jpeg(30, 40, Color.GREEN));
        SendChatMessageRequest request = photo(nextClientId(), mediaId, "form check?");
        request.setRecipient(BOB);

        List<Sent> sent = capture(() -> controller.sendDM(request, as(ALICE)));

        JsonNode toBob = only(sent, "/user/" + BOB + "/queue/messages");
        JsonNode toAlice = only(sent, "/user/" + ALICE + "/queue/messages");
        assertTrue(sent.stream().noneMatch(s -> s.destination().equals(TOPIC)));
        assertTrue(toBob.at("/media/url").asText().startsWith("/api/messaging/media/" + mediaId + "/raw"));
        assertEquals(toBob.get("id"), toAlice.get("id"));
        long id = toBob.get("id").asLong();

        JsonNode inBobsHistory = findById(getJson(bobToken, "/api/messaging/dm/" + ALICE), id);
        assertEquals("form check?", inBobsHistory.get("content").asText());
        mvc.perform(get(inBobsHistory.at("/media/url").asText())).andExpect(status().isOk());
        // Carol's conversation with Alice doesn't include it
        assertTrue(idsOf(getJson(carolToken, "/api/messaging/dm/" + ALICE)).stream().noneMatch(i -> i == id));

        JsonNode inbox = getJson(bobToken, "/api/messaging/dm/conversations");
        JsonNode withAlice = null;
        for (JsonNode c : inbox) if (c.get("partnerUsername").asText().equals(ALICE)) withAlice = c;
        assertNotNull(withAlice);
        assertEquals("Photo", withAlice.get("lastMessage").asText());
        assertEquals("IMAGE", withAlice.get("lastKind").asText());
    }

    @Test
    void communityHistoryCarriesAFreshUrlForEachPhoto() throws Exception {
        long mediaId = uploadPhoto(aliceToken, jpeg(20, 20, Color.BLUE));
        long id = only(capture(() -> controller.sendCommunity(photo(nextClientId(), mediaId, "pr"), as(ALICE))), TOPIC)
                .get("id").asLong();

        JsonNode inHistory = findById(getJson(bobToken, "/api/messaging/community/history"), id);
        assertEquals(mediaId, inHistory.at("/media/mediaId").asLong());
        mvc.perform(get(inHistory.at("/media/url").asText())).andExpect(status().isOk());
    }

    // ── Deleting and moderation ────────────────────────────────────────────

    @Test
    void aDeletedPhotoIsBlankedForMembersButStaffCanStillReviewIt() throws Exception {
        long mediaId = uploadPhoto(aliceToken, jpeg(20, 20, Color.BLUE));
        long id = only(capture(() -> controller.sendCommunity(photo(nextClientId(), mediaId, "rude caption"), as(ALICE))), TOPIC)
                .get("id").asLong();
        mvc.perform(json(post("/api/messaging/community/messages/" + id + "/report")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken)), Map.of("reason", "INAPPROPRIATE")))
                .andExpect(status().isCreated());

        JsonNode deleted = only(capture(() -> mvc.perform(delete("/api/messaging/community/messages/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))).andExpect(status().isOk())), TOPIC);
        assertTrue(deleted.get("deleted").asBoolean());
        assertTrue(deleted.get("media").isNull());
        assertEquals("", deleted.get("content").asText());
        JsonNode inHistory = findById(getJson(bobToken, "/api/messaging/community/history"), id);
        assertTrue(inHistory.get("media").isNull());
        assertEquals("", inHistory.get("content").asText());

        JsonNode group = groupFor(getJson(adminToken, "/api/admin/chat-reports"), id);
        assertEquals("IMAGE", group.get("kind").asText());
        assertEquals("rude caption", group.get("content").asText());
        assertTrue(group.get("deletedBySender").asBoolean());
        String adminUrl = group.at("/media/url").asText();
        // Absolute, because the dashboard runs on another origin than the backend
        assertTrue(adminUrl.startsWith("http://localhost/api/messaging/media/" + mediaId + "/raw?"), adminUrl);
        mvc.perform(get(adminUrl.substring("http://localhost".length()))).andExpect(status().isOk());
    }

    @Test
    void staffRemovingAPhotoBlanksItLive() throws Exception {
        long mediaId = uploadPhoto(aliceToken, jpeg(20, 20, Color.BLUE));
        long id = only(capture(() -> controller.sendCommunity(photo(nextClientId(), mediaId, null), as(ALICE))), TOPIC)
                .get("id").asLong();
        mvc.perform(json(post("/api/messaging/community/messages/" + id + "/report")
                        .header(HttpHeaders.AUTHORIZATION, bearer(carolToken)), Map.of("reason", "SPAM")))
                .andExpect(status().isCreated());

        List<Sent> sent = capture(() -> mvc.perform(put("/api/admin/chat-reports/community/" + id + "/remove")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))).andExpect(status().isOk()));

        JsonNode live = only(sent, TOPIC);
        assertTrue(live.get("removedByStaff").asBoolean());
        assertTrue(live.get("media").isNull());
        JsonNode group = groupFor(getJson(adminToken, "/api/admin/chat-reports?includeResolved=true"), id);
        assertTrue(group.at("/media/url").asText().startsWith("http://localhost/api/messaging/media/"));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private record Sent(String destination, JsonNode body) {}

    private interface Action {
        void run() throws Exception;
    }

    private String nextClientId() {
        return "ph-" + System.nanoTime() + "-" + (++clientIds);
    }

    private static Principal as(String username) {
        return () -> username;
    }

    private static SendChatMessageRequest photo(String clientId, Long mediaId, String caption) {
        SendChatMessageRequest request = new SendChatMessageRequest();
        request.setClientId(clientId);
        request.setKind(MessageKind.IMAGE);
        request.setContent(caption);
        SendChatMessageRequest.MediaPayload media = new SendChatMessageRequest.MediaPayload();
        media.setMediaId(mediaId);
        request.setMedia(media);
        return request;
    }

    private ResultActions upload(String token, byte[] bytes) throws Exception {
        return mvc.perform(multipart(UPLOAD)
                .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long uploadPhoto(String token, byte[] bytes) throws Exception {
        return json(upload(token, bytes).andExpect(status().isCreated())).get("mediaId").asLong();
    }

    private void assertRejected(SendChatMessageRequest request, String sender, String code) throws Exception {
        List<Sent> sent = capture(() -> controller.sendCommunity(request, as(sender)));
        JsonNode error = only(sent, "/user/" + sender + "/queue/errors");
        assertEquals(code, error.get("code").asText());
        assertEquals(request.getClientId(), error.get("clientId").asText());
        assertEquals(1, sent.size(), "nothing but the error is sent");
    }

    /** Runs the action and returns the chat messages it sent to the broker. */
    private List<Sent> capture(Action action) throws Exception {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        ChannelInterceptor capture = new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
                if (destination != null && (destination.equals(TOPIC) || destination.startsWith("/user/"))) {
                    try {
                        sent.add(new Sent(destination, objectMapper.readTree((byte[]) message.getPayload())));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                return message;
            }
        };
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
        List<Long> ids = new java.util.ArrayList<>();
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

    private static JsonNode groupFor(JsonNode groups, long messageId) {
        for (JsonNode g : groups) {
            if (g.get("channel").asText().equals("COMMUNITY") && g.get("messageId").asLong() == messageId) return g;
        }
        fail("no report group for message " + messageId);
        return null;
    }
}
