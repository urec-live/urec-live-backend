package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.dto.ChatMessageResponse;
import com.ureclive.urec_live_backend.dto.ConversationSummary;
import com.ureclive.urec_live_backend.entity.CommunityMessage;
import com.ureclive.urec_live_backend.entity.DirectMessage;
import com.ureclive.urec_live_backend.repository.CommunityMessageRepository;
import com.ureclive.urec_live_backend.repository.DirectMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class ChatMessagingController {

    @Autowired private CommunityMessageRepository communityRepo;
    @Autowired private DirectMessageRepository dmRepo;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    // ── STOMP: community send ──────────────────────────────────────────────

    @MessageMapping("/chat.community.send")
    public void sendCommunity(@Payload Map<String, String> payload, Principal principal) {
        if (principal == null) return;
        String content = payload.get("content");
        if (content == null || content.isBlank()) return;

        CommunityMessage msg = new CommunityMessage();
        msg.setSenderUsername(principal.getName());
        msg.setContent(content.trim());
        communityRepo.save(msg);

        messagingTemplate.convertAndSend("/topic/chat.community", ChatMessageResponse.from(msg));
    }

    // ── STOMP: DM send ─────────────────────────────────────────────────────

    @MessageMapping("/chat.dm.send")
    public void sendDM(@Payload Map<String, String> payload, Principal principal) {
        if (principal == null) return;
        String recipient = payload.get("recipient");
        String content = payload.get("content");
        if (recipient == null || content == null || content.isBlank()) return;

        DirectMessage msg = new DirectMessage();
        msg.setSenderUsername(principal.getName());
        msg.setRecipientUsername(recipient);
        msg.setContent(content.trim());
        dmRepo.save(msg);

        ChatMessageResponse resp = ChatMessageResponse.from(msg);
        messagingTemplate.convertAndSendToUser(recipient, "/queue/messages", resp);
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/messages", resp);
    }

    // ── REST: community history ────────────────────────────────────────────

    @GetMapping("/api/messaging/community/history")
    public List<ChatMessageResponse> getCommunityHistory() {
        return communityRepo.findTop50ByOrderBySentAtAsc()
                .stream().map(ChatMessageResponse::from).collect(Collectors.toList());
    }

    // ── REST: DM conversation history ─────────────────────────────────────

    @GetMapping("/api/messaging/dm/{username}")
    public List<ChatMessageResponse> getDMHistory(
            @PathVariable String username,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails me) {
        return dmRepo.findConversation(me.getUsername(), username)
                .stream().map(ChatMessageResponse::from).collect(Collectors.toList());
    }

    // ── REST: DM inbox ────────────────────────────────────────────────────

    @GetMapping("/api/messaging/dm/conversations")
    public List<ConversationSummary> getConversations(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails me) {
        List<DirectMessage> all = dmRepo.findAllInvolving(me.getUsername());

        // Deduplicate: keep latest message per conversation partner
        Map<String, DirectMessage> latestByPartner = new LinkedHashMap<>();
        for (DirectMessage m : all) {
            String partner = m.getSenderUsername().equals(me.getUsername())
                    ? m.getRecipientUsername() : m.getSenderUsername();
            latestByPartner.putIfAbsent(partner, m);
        }

        return latestByPartner.entrySet().stream().map(e -> {
            ConversationSummary s = new ConversationSummary();
            s.partnerUsername = e.getKey();
            s.lastMessage = e.getValue().getContent();
            s.lastSentAt = e.getValue().getSentAt().toString();
            // unread = any message from partner to me that is unread
            s.unread = all.stream().anyMatch(m ->
                    m.getSenderUsername().equals(e.getKey()) &&
                    m.getRecipientUsername().equals(me.getUsername()) &&
                    !m.getRead());
            return s;
        }).collect(Collectors.toList());
    }

    // ── REST: mark conversation read ──────────────────────────────────────

    @Transactional
    @PatchMapping("/api/messaging/dm/{username}/read")
    public void markRead(
            @PathVariable String username,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails me) {
        dmRepo.markAllRead(username, me.getUsername());
    }
}
