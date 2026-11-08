package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.ChatMessageResponse;
import com.ureclive.urec_live_backend.dto.ChatReportResponse;
import com.ureclive.urec_live_backend.dto.ConversationSummary;
import com.ureclive.urec_live_backend.dto.ReportMessageRequest;
import com.ureclive.urec_live_backend.dto.SendChatMessageRequest;
import com.ureclive.urec_live_backend.entity.ChatChannel;
import com.ureclive.urec_live_backend.entity.ChatMessageBase;
import com.ureclive.urec_live_backend.entity.ChatMessageReport;
import com.ureclive.urec_live_backend.entity.CommunityMessage;
import com.ureclive.urec_live_backend.entity.DirectMessage;
import com.ureclive.urec_live_backend.entity.MessageKind;
import com.ureclive.urec_live_backend.entity.MessageMedia;
import com.ureclive.urec_live_backend.entity.MessageReaction;
import com.ureclive.urec_live_backend.repository.ChatMessageReportRepository;
import com.ureclive.urec_live_backend.repository.CommunityMessageRepository;
import com.ureclive.urec_live_backend.repository.DirectMessageRepository;
import com.ureclive.urec_live_backend.repository.MessageReactionRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Community chat and direct messages: sending, history, reactions, deleting and reporting.
 * Every change re-broadcasts the whole message on the channel's live destination, and the app
 * replaces its copy by id, so one payload shape covers new messages and updates alike.
 */
@Service
public class ChatMessageService {

    public static final String COMMUNITY_TOPIC = "/topic/chat.community";
    /** Per-user queue for DMs; resolves to /user/{username}/queue/messages. */
    public static final String USER_QUEUE = "/queue/messages";
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;
    static final int MAX_MEDIA_URL_LENGTH = 1000;
    static final int MAX_MEDIA_DIMENSION = 4096;
    static final int MAX_PROVIDER_ID_LENGTH = 100;
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_-]{1," + SendChatMessageRequest.MAX_CLIENT_ID_LENGTH + "}");

    private final CommunityMessageRepository communityRepo;
    private final DirectMessageRepository dmRepo;
    private final MessageReactionRepository reactionRepo;
    private final ChatMessageReportRepository reportRepo;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final KlipyClient klipy;
    private final ActivityLogService activityLogService;

    @Autowired
    public ChatMessageService(
            CommunityMessageRepository communityRepo,
            DirectMessageRepository dmRepo,
            MessageReactionRepository reactionRepo,
            ChatMessageReportRepository reportRepo,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            KlipyClient klipy,
            ActivityLogService activityLogService) {
        this.communityRepo = communityRepo;
        this.dmRepo = dmRepo;
        this.reactionRepo = reactionRepo;
        this.reportRepo = reportRepo;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.klipy = klipy;
        this.activityLogService = activityLogService;
    }

    // ── Sending ────────────────────────────────────────────────────────────

    /** Saves and broadcasts a community message. A retry with a clientId already saved re-broadcasts that message. */
    public ChatMessageResponse sendCommunity(String sender, SendChatMessageRequest request) {
        CommunityMessage message = new CommunityMessage();
        message.setSenderUsername(sender);
        fill(message, request);
        return saveOnce(message, communityRepo::save, communityRepo::findFirstBySenderUsernameAndClientId);
    }

    /** Saves a DM and sends it to both people's queues. The recipient must exist and can't be the sender. */
    public ChatMessageResponse sendDirect(String sender, SendChatMessageRequest request) {
        String recipient = request.getRecipient() == null ? "" : request.getRecipient().trim();
        if (recipient.equals(sender)) {
            throw new ChatSendException("INVALID_RECIPIENT", "You can't message yourself");
        }
        if (recipient.isEmpty() || !Boolean.TRUE.equals(userRepository.existsByUsername(recipient))) {
            throw new ChatSendException("UNKNOWN_RECIPIENT", "That member doesn't exist");
        }

        DirectMessage message = new DirectMessage();
        message.setSenderUsername(sender);
        message.setRecipientUsername(recipient);
        fill(message, request);
        return saveOnce(message, dmRepo::save, dmRepo::findFirstBySenderUsernameAndClientId);
    }

    /**
     * Saves and broadcasts a new message, or re-broadcasts the one already saved under its clientId.
     * Not transactional, so when two copies of a retry race past the lookup, the unique
     * (sender, clientId) constraint rejects the second insert here and the first is sent instead.
     */
    private <M extends ChatMessageBase> ChatMessageResponse saveOnce(
            M message, UnaryOperator<M> save, BiFunction<String, String, Optional<M>> findByClientId) {
        String clientId = message.getClientId();
        if (clientId != null) {
            Optional<M> existing = findByClientId.apply(message.getSenderUsername(), clientId);
            if (existing.isPresent()) return broadcast(existing.get());
        }
        try {
            save.apply(message);
        } catch (DataIntegrityViolationException e) {
            if (clientId == null) throw e;
            return broadcast(findByClientId.apply(message.getSenderUsername(), clientId).orElseThrow(() -> e));
        }
        afterSend(message);
        return broadcast(message);
    }

    /** Validates the request and copies it onto the new message. */
    private void fill(ChatMessageBase message, SendChatMessageRequest request) {
        message.setClientId(validClientId(request.getClientId()));
        MessageKind kind = request.getKind();
        message.setKind(kind);
        if (kind.hasMedia()) {
            message.setContent("");
            message.setMedia(validMedia(request.getMedia()));
            return;
        }
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new ChatSendException("EMPTY", "Message is empty");
        }
        if (content.length() > SendChatMessageRequest.MAX_CONTENT_LENGTH) {
            throw new ChatSendException("TOO_LONG",
                    "Messages can be at most " + SendChatMessageRequest.MAX_CONTENT_LENGTH + " characters");
        }
        message.setContent(content);
    }

    private static String validClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) return null;
        String trimmed = clientId.trim();
        if (!CLIENT_ID.matcher(trimmed).matches()) {
            throw new ChatSendException("INVALID_CLIENT_ID", "Invalid message id");
        }
        return trimmed;
    }

    /** Only KLIPY URLs are accepted, so a "GIF" can't be an arbitrary link that skipped the content filter. */
    private MessageMedia validMedia(SendChatMessageRequest.MediaPayload media) {
        if (media == null || media.getUrl() == null || media.getUrl().isBlank()) {
            throw new ChatSendException("INVALID_MEDIA", "Pick a GIF or sticker to send");
        }
        String url = media.getUrl().trim();
        String previewUrl = media.getPreviewUrl() == null || media.getPreviewUrl().isBlank()
                ? null : media.getPreviewUrl().trim();
        if (!isAcceptableMediaUrl(url) || (previewUrl != null && !isAcceptableMediaUrl(previewUrl))) {
            throw new ChatSendException("INVALID_MEDIA", "That GIF can't be sent");
        }
        String providerId = media.getProviderId() == null ? null : media.getProviderId().trim();
        if (providerId != null && (providerId.isEmpty() || providerId.length() > MAX_PROVIDER_ID_LENGTH)) {
            providerId = null;
        }
        return new MessageMedia(url, previewUrl, dimension(media.getWidth()), dimension(media.getHeight()), providerId);
    }

    private boolean isAcceptableMediaUrl(String url) {
        return url.length() <= MAX_MEDIA_URL_LENGTH && klipy.isAllowedMediaUrl(url);
    }

    /** Sizes only shape the bubble, so a bad one is dropped rather than failing the send. */
    private static Integer dimension(Integer value) {
        return value != null && value > 0 && value <= MAX_MEDIA_DIMENSION ? value : null;
    }

    private void afterSend(ChatMessageBase message) {
        MessageMedia media = message.getMedia();
        if (media == null || media.getProviderId() == null) return;
        KlipyClient.Category category =
                message.getKind() == MessageKind.STICKER ? KlipyClient.Category.STICKERS : KlipyClient.Category.GIFS;
        klipy.reportShare(category, media.getProviderId(), message.getSenderUsername());
    }

    // ── History ────────────────────────────────────────────────────────────

    /**
     * The newest {@code limit} community messages before {@code before} (or the very newest when null),
     * oldest first within the page.
     */
    public List<ChatMessageResponse> communityHistory(Long before, Integer limit) {
        List<CommunityMessage> page =
                communityRepo.findByIdLessThanOrderByIdDesc(cursor(before), PageRequest.of(0, pageSize(limit)));
        return toResponses(ChatChannel.COMMUNITY, oldestFirst(page));
    }

    /** Same paging as {@link #communityHistory}, for one conversation. */
    public List<ChatMessageResponse> directHistory(String me, String partner, Long before, Integer limit) {
        List<DirectMessage> page =
                dmRepo.findConversationPage(me, partner, cursor(before), PageRequest.of(0, pageSize(limit)));
        return toResponses(ChatChannel.DM, oldestFirst(page));
    }

    private static long cursor(Long before) {
        return before == null || before <= 0 ? Long.MAX_VALUE : before;
    }

    private static int pageSize(Integer limit) {
        if (limit == null) return DEFAULT_PAGE_SIZE;
        return Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
    }

    private static <T> List<T> oldestFirst(List<T> newestFirst) {
        List<T> list = new ArrayList<>(newestFirst);
        Collections.reverse(list);
        return list;
    }

    /** The inbox: one row per conversation partner, newest first. */
    public List<ConversationSummary> conversations(String me) {
        List<DirectMessage> all = dmRepo.findAllInvolving(me);

        // Deduplicate: keep latest message per conversation partner
        Map<String, DirectMessage> latestByPartner = new LinkedHashMap<>();
        for (DirectMessage m : all) {
            String partner = m.getSenderUsername().equals(me) ? m.getRecipientUsername() : m.getSenderUsername();
            latestByPartner.putIfAbsent(partner, m);
        }

        return latestByPartner.entrySet().stream().map(e -> {
            DirectMessage last = e.getValue();
            ConversationSummary s = new ConversationSummary();
            s.partnerUsername = e.getKey();
            s.lastMessage = preview(last);
            s.lastKind = last.getKind().name();
            s.lastSentAt = last.getSentAt().toString();
            // unread = any message from partner to me that is unread
            s.unread = all.stream().anyMatch(m ->
                    m.getSenderUsername().equals(e.getKey())
                            && m.getRecipientUsername().equals(me)
                            && !m.getRead());
            return s;
        }).collect(Collectors.toList());
    }

    static String preview(ChatMessageBase message) {
        if (message.isRemovedByStaff()) return "Message removed";
        if (message.getDeletedAt() != null) return "Message deleted";
        return switch (message.getKind()) {
            case GIF -> "GIF";
            case STICKER -> "Sticker";
            case TEXT -> message.getContent();
        };
    }

    @Transactional
    public void markRead(String me, String partner) {
        dmRepo.markAllRead(partner, me);
    }

    // ── Reactions ──────────────────────────────────────────────────────────

    /**
     * Sets the member's reaction, replacing any other emoji they had on the message. Not
     * transactional (like {@link #report}), so a double tap's second insert can be caught here.
     */
    public ChatMessageResponse react(ChatChannel channel, Long messageId, String me, String emoji) {
        String normalized = MessageReaction.normalizeEmoji(emoji);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pick one of " + String.join(" ", MessageReaction.ALLOWED_EMOJI));
        }
        ChatMessageBase message = visibleMessage(channel, messageId, me);
        if (message.isHidden()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This message was deleted");
        }
        MessageReaction reaction = reactionRepo.findByChannelAndMessageIdAndUsername(channel, messageId, me).orElse(null);
        if (reaction == null) {
            try {
                reactionRepo.save(new MessageReaction(channel, messageId, me, normalized));
            } catch (DataIntegrityViolationException e) {
                // Two taps at once: the unique constraint caught the second, so update the one the first saved
                reaction = reactionRepo.findByChannelAndMessageIdAndUsername(channel, messageId, me).orElseThrow(() -> e);
            }
        }
        if (reaction != null && !reaction.getEmoji().equals(normalized)) {
            reaction.setEmoji(normalized);
            reaction.setCreatedAt(Instant.now());
            reactionRepo.save(reaction);
        }
        return broadcast(message);
    }

    /** Removes the member's reaction, if any. */
    @Transactional
    public ChatMessageResponse unreact(ChatChannel channel, Long messageId, String me) {
        ChatMessageBase message = visibleMessage(channel, messageId, me);
        reactionRepo.findByChannelAndMessageIdAndUsername(channel, messageId, me).ifPresent(reactionRepo::delete);
        return broadcast(message);
    }

    // ── Deleting ───────────────────────────────────────────────────────────

    /** The sender hides their own message. The row is kept so staff can still review reports on it. */
    @Transactional
    public ChatMessageResponse deleteOwn(ChatChannel channel, Long messageId, String me) {
        ChatMessageBase message = visibleMessage(channel, messageId, me);
        if (!message.getSenderUsername().equals(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own messages");
        }
        if (message.getDeletedAt() == null) {
            message.setDeletedAt(Instant.now());
            saveMessage(message);
        }
        return broadcast(message);
    }

    // ── Reporting ──────────────────────────────────────────────────────────

    /** Files a report for staff. Members can't report their own messages, or the same message twice. */
    public ChatReportResponse report(ChatChannel channel, Long messageId, String me, ReportMessageRequest request) {
        ChatMessageBase message = visibleMessage(channel, messageId, me);
        if (message.getSenderUsername().equals(me)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can't report your own message");
        }
        if (message.isHidden()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This message was already removed");
        }
        if (reportRepo.existsByChannelAndMessageIdAndReporterUsername(channel, messageId, me)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You've already reported this message");
        }
        String note = request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim();
        ChatMessageReport report;
        try {
            report = reportRepo.save(new ChatMessageReport(channel, messageId, me, request.getReason(), note));
        } catch (DataIntegrityViolationException e) {
            // Two taps at once: the unique constraint caught the second
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You've already reported this message");
        }
        activityLogService.log("CHAT_MESSAGE_REPORTED", me,
                "Reported a " + describe(channel) + " from " + message.getSenderUsername(), null);
        return ChatReportResponse.from(report);
    }

    // ── Shared with the admin side ─────────────────────────────────────────

    /** "community message" or "direct message", for activity log lines. */
    public static String describe(ChatChannel channel) {
        return channel == ChatChannel.DM ? "direct message" : "community message";
    }

    public Optional<ChatMessageBase> findMessage(ChatChannel channel, Long messageId) {
        return switch (channel) {
            case COMMUNITY -> communityRepo.findById(messageId).map(m -> (ChatMessageBase) m);
            case DM -> dmRepo.findById(messageId).map(m -> (ChatMessageBase) m);
        };
    }

    /** Loads several messages of one channel in one query, by id. */
    public Map<Long, ChatMessageBase> findMessages(ChatChannel channel, Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<? extends ChatMessageBase> found = switch (channel) {
            case COMMUNITY -> communityRepo.findAllById(ids);
            case DM -> dmRepo.findAllById(ids);
        };
        return found.stream().collect(Collectors.toMap(ChatMessageBase::getId, Function.identity()));
    }

    public void saveMessage(ChatMessageBase message) {
        if (message instanceof DirectMessage dm) dmRepo.save(dm);
        else communityRepo.save((CommunityMessage) message);
    }

    /** Sends the message's current state to everyone who can see it, and returns that state. */
    public ChatMessageResponse broadcast(ChatMessageBase message) {
        ChatMessageResponse response = ChatMessageResponse.from(
                message, reactionsFor(message.getChannel(), List.of(message.getId())).getOrDefault(message.getId(), Map.of()));
        if (message instanceof DirectMessage dm) {
            messagingTemplate.convertAndSendToUser(dm.getRecipientUsername(), USER_QUEUE, response);
            messagingTemplate.convertAndSendToUser(dm.getSenderUsername(), USER_QUEUE, response);
        } else {
            messagingTemplate.convertAndSend(COMMUNITY_TOPIC, response);
        }
        return response;
    }

    /** A community message, or a DM the member is part of; anything else is a 404, as if it didn't exist. */
    private ChatMessageBase visibleMessage(ChatChannel channel, Long messageId, String me) {
        Optional<ChatMessageBase> message = switch (channel) {
            case COMMUNITY -> communityRepo.findById(messageId).map(m -> (ChatMessageBase) m);
            case DM -> dmRepo.findById(messageId).filter(m -> m.involves(me)).map(m -> (ChatMessageBase) m);
        };
        return message.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
    }

    private List<ChatMessageResponse> toResponses(ChatChannel channel, List<? extends ChatMessageBase> messages) {
        Map<Long, Map<String, List<String>>> reactions =
                reactionsFor(channel, messages.stream().map(ChatMessageBase::getId).toList());
        return messages.stream()
                .map(m -> ChatMessageResponse.from(m, reactions.getOrDefault(m.getId(), Map.of())))
                .collect(Collectors.toList());
    }

    /** Message id to (emoji to usernames), for a whole page in one query. */
    private Map<Long, Map<String, List<String>>> reactionsFor(ChatChannel channel, Collection<Long> messageIds) {
        if (messageIds.isEmpty()) return Map.of();
        Map<Long, Map<String, List<String>>> byMessage = new HashMap<>();
        for (MessageReaction r : reactionRepo.findByChannelAndMessageIdInOrderByCreatedAtAscIdAsc(channel, messageIds)) {
            byMessage.computeIfAbsent(r.getMessageId(), id -> new LinkedHashMap<>())
                    .computeIfAbsent(r.getEmoji(), emoji -> new ArrayList<>())
                    .add(r.getUsername());
        }
        return byMessage;
    }
}
