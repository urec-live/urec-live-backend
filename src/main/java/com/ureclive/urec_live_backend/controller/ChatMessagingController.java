package com.ureclive.urec_live_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ureclive.urec_live_backend.dto.ChatErrorResponse;
import com.ureclive.urec_live_backend.dto.ChatMessageResponse;
import com.ureclive.urec_live_backend.dto.ChatReportResponse;
import com.ureclive.urec_live_backend.dto.ConversationSummary;
import com.ureclive.urec_live_backend.dto.ReactionRequest;
import com.ureclive.urec_live_backend.dto.ReportMessageRequest;
import com.ureclive.urec_live_backend.dto.SendChatMessageRequest;
import com.ureclive.urec_live_backend.entity.ChatChannel;
import com.ureclive.urec_live_backend.service.ChatMessageService;
import com.ureclive.urec_live_backend.service.ChatSendException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.function.Supplier;

@RestController
public class ChatMessagingController {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessagingController.class);

    /** Rejected sends go here, resolving to /user/{username}/queue/errors. */
    public static final String ERROR_QUEUE = "/queue/errors";

    private final ChatMessageService chat;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ChatMessagingController(
            ChatMessageService chat, SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.chat = chat;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    // ── STOMP: sends ───────────────────────────────────────────────────────

    @MessageMapping("/chat.community.send")
    public void sendCommunity(@Payload SendChatMessageRequest request, Principal principal) {
        send(request, principal, () -> chat.sendCommunity(principal.getName(), request));
    }

    @MessageMapping("/chat.dm.send")
    public void sendDM(@Payload SendChatMessageRequest request, Principal principal) {
        send(request, principal, () -> chat.sendDirect(principal.getName(), request));
    }

    /** Runs the send; a rejection goes back to the sender's error queue with their clientId. */
    private void send(SendChatMessageRequest request, Principal principal, Supplier<ChatMessageResponse> action) {
        // A connection without a valid token has no user to send as, or to tell about it
        if (principal == null) return;
        try {
            action.get();
        } catch (ChatSendException e) {
            sendError(principal, request, e.getCode(), e.getReason());
        } catch (RuntimeException e) {
            logger.error("[Chat] send from {} failed", principal.getName(), e);
            sendError(principal, request, "SEND_FAILED", "Couldn't send that message. Try again.");
        }
    }

    private void sendError(Principal principal, SendChatMessageRequest request, String code, String message) {
        sendError(principal, request.getClientId(), code, message);
    }

    private void sendError(Principal principal, String clientId, String code, String message) {
        messagingTemplate.convertAndSendToUser(principal.getName(), ERROR_QUEUE,
                new ChatErrorResponse(clientId, code, message));
    }

    /**
     * A send whose JSON doesn't fit the request (an unknown kind, a width that isn't a number, an
     * empty frame) fails before the handler runs, so it never reaches {@link #send}. It goes to the
     * error queue too, carrying the clientId when the raw JSON has one, so the app can mark that bubble.
     */
    @MessageExceptionHandler({MessageConversionException.class, MethodArgumentNotValidException.class})
    public void unreadableSend(Exception e, Message<?> message, Principal principal) {
        if (principal == null) return;
        logger.warn("[Chat] unreadable send from {}: {}", principal.getName(), e.getMessage());
        sendError(principal, clientIdOf(message.getPayload()), "INVALID_MESSAGE", "Couldn't send that message");
    }

    private String clientIdOf(Object payload) {
        try {
            JsonNode json = payload instanceof byte[] bytes
                    ? objectMapper.readTree(bytes)
                    : objectMapper.readTree(String.valueOf(payload));
            JsonNode clientId = json == null ? null : json.get("clientId");
            return clientId != null && clientId.isTextual() ? clientId.asText() : null;
        } catch (IOException e) {
            return null;
        }
    }

    // ── REST: history ──────────────────────────────────────────────────────

    /** GET /api/messaging/community/history?before={id}&limit=50 — newest page, oldest first within it */
    @GetMapping("/api/messaging/community/history")
    public List<ChatMessageResponse> getCommunityHistory(
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit) {
        return chat.communityHistory(before, limit);
    }

    /** GET /api/messaging/dm/{username}?before={id}&limit=50 — same paging, for one conversation */
    @GetMapping("/api/messaging/dm/{username}")
    public List<ChatMessageResponse> getDMHistory(
            @PathVariable String username,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit,
            Authentication auth) {
        return chat.directHistory(auth.getName(), username, before, limit);
    }

    /** GET /api/messaging/dm/conversations — the DM inbox */
    @GetMapping("/api/messaging/dm/conversations")
    public List<ConversationSummary> getConversations(Authentication auth) {
        return chat.conversations(auth.getName());
    }

    /** PATCH /api/messaging/dm/{username}/read — mark everything they sent me as read */
    @PatchMapping("/api/messaging/dm/{username}/read")
    public void markRead(@PathVariable String username, Authentication auth) {
        chat.markRead(auth.getName(), username);
    }

    // ── REST: reactions, delete, report ────────────────────────────────────

    /** PUT /api/messaging/{community|dm}/messages/{id}/reaction — set or switch my reaction */
    @PutMapping("/api/messaging/{channel}/messages/{id}/reaction")
    public ChatMessageResponse react(
            @PathVariable String channel,
            @PathVariable Long id,
            @Valid @RequestBody ReactionRequest request,
            Authentication auth) {
        return chat.react(ChatChannel.fromPath(channel), id, auth.getName(), request.getEmoji());
    }

    /** DELETE /api/messaging/{community|dm}/messages/{id}/reaction — remove my reaction */
    @DeleteMapping("/api/messaging/{channel}/messages/{id}/reaction")
    public ChatMessageResponse unreact(@PathVariable String channel, @PathVariable Long id, Authentication auth) {
        return chat.unreact(ChatChannel.fromPath(channel), id, auth.getName());
    }

    /** DELETE /api/messaging/{community|dm}/messages/{id} — delete my own message */
    @DeleteMapping("/api/messaging/{channel}/messages/{id}")
    public ChatMessageResponse deleteMessage(@PathVariable String channel, @PathVariable Long id, Authentication auth) {
        return chat.deleteOwn(ChatChannel.fromPath(channel), id, auth.getName());
    }

    /** POST /api/messaging/{community|dm}/messages/{id}/report — report someone else's message to staff */
    @PostMapping("/api/messaging/{channel}/messages/{id}/report")
    public ResponseEntity<ChatReportResponse> report(
            @PathVariable String channel,
            @PathVariable Long id,
            @Valid @RequestBody ReportMessageRequest request,
            Authentication auth) {
        ChatReportResponse response = chat.report(ChatChannel.fromPath(channel), id, auth.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
