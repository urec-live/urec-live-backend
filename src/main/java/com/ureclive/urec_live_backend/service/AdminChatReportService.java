package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.ChatMessageResponse;
import com.ureclive.urec_live_backend.dto.ChatReportGroupResponse;
import com.ureclive.urec_live_backend.dto.ChatReportResponse;
import com.ureclive.urec_live_backend.dto.ChatReportSummaryResponse;
import com.ureclive.urec_live_backend.entity.ChatChannel;
import com.ureclive.urec_live_backend.entity.ChatMessageBase;
import com.ureclive.urec_live_backend.entity.ChatMessageReport;
import com.ureclive.urec_live_backend.entity.DirectMessage;
import com.ureclive.urec_live_backend.entity.ReportStatus;
import com.ureclive.urec_live_backend.repository.ChatMessageReportRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Staff side of chat moderation: the reported-messages queue, dismissing reports and removing messages. */
@Service
public class AdminChatReportService {

    private final ChatMessageReportRepository reportRepo;
    private final ChatMessageService chat;
    private final ActivityLogService activityLogService;

    private record MessageKey(ChatChannel channel, Long messageId) {}

    @Autowired
    public AdminChatReportService(
            ChatMessageReportRepository reportRepo,
            ChatMessageService chat,
            ActivityLogService activityLogService) {
        this.reportRepo = reportRepo;
        this.chat = chat;
        this.activityLogService = activityLogService;
    }

    /**
     * Reports grouped by message. Messages with open reports come first, then by the newest report.
     * By default only open reports are included.
     */
    public List<ChatReportGroupResponse> getGrouped(boolean includeResolved) {
        List<ChatMessageReport> reports = onKnownChannels(includeResolved
                ? reportRepo.findAllByOrderByReportedAtDesc()
                : reportRepo.findByStatusOrderByReportedAtDesc(ReportStatus.OPEN));

        Map<MessageKey, List<ChatMessageReport>> byMessage = new LinkedHashMap<>();
        for (ChatMessageReport report : reports) {
            byMessage.computeIfAbsent(new MessageKey(report.getChannel(), report.getMessageId()), k -> new ArrayList<>())
                    .add(report);
        }

        // One query per channel for the messages themselves
        Map<ChatChannel, Set<Long>> idsByChannel = new EnumMap<>(ChatChannel.class);
        byMessage.keySet().forEach(k -> idsByChannel.computeIfAbsent(k.channel(), c -> new LinkedHashSet<>()).add(k.messageId()));
        Map<ChatChannel, Map<Long, ChatMessageBase>> messages = new EnumMap<>(ChatChannel.class);
        idsByChannel.forEach((channel, ids) -> messages.put(channel, chat.findMessages(channel, ids)));

        return byMessage.entrySet().stream()
                .map(e -> toGroup(e.getKey(),
                        messages.getOrDefault(e.getKey().channel(), Map.of()).get(e.getKey().messageId()),
                        e.getValue()))
                .sorted(Comparator.comparing((ChatReportGroupResponse g) -> g.openCount == 0)
                        .thenComparing(g -> g.latestReportedAt, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    /** Counts for the sidebar badge (polled, so it stays cheap). */
    public ChatReportSummaryResponse getSummary() {
        List<ChatMessageReport> open = onKnownChannels(reportRepo.findByStatusOrderByReportedAtDesc(ReportStatus.OPEN));
        long messages = open.stream().map(r -> new MessageKey(r.getChannel(), r.getMessageId())).distinct().count();
        return new ChatReportSummaryResponse(messages, open.size());
    }

    /** Closes the message's open reports and leaves the message up. */
    @Transactional
    public ChatReportGroupResponse dismiss(ChatChannel channel, Long messageId, String staffUsername) {
        List<ChatMessageReport> reports = reportsFor(channel, messageId);
        int closed = resolveOpen(reports, ReportStatus.DISMISSED, staffUsername);
        if (closed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This message has no open reports");
        }
        saveOrConflict(reports);

        ChatMessageBase message = chat.findMessage(channel, messageId).orElse(null);
        activityLogService.log("CHAT_REPORT_DISMISSED", staffUsername,
                "Dismissed " + closed + (closed == 1 ? " report" : " reports") + " on a " + ChatMessageService.describe(channel)
                        + (message == null ? "" : " from " + message.getSenderUsername()),
                null);
        return toGroup(new MessageKey(channel, messageId), message, reports);
    }

    /**
     * Hides the message from members ("Removed by staff") and closes its open reports as actioned.
     * Members who have the chat open see the change live.
     */
    @Transactional
    public ChatReportGroupResponse remove(ChatChannel channel, Long messageId, String staffUsername) {
        List<ChatMessageReport> reports = reportsFor(channel, messageId);
        ChatMessageBase message = chat.findMessage(channel, messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        boolean newlyRemoved = !message.isRemovedByStaff();
        int closed = resolveOpen(reports, ReportStatus.ACTIONED, staffUsername);
        if (!newlyRemoved && closed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This message was already removed");
        }
        // Before the message changes, so a lost race leaves it untouched and nothing is broadcast
        saveOrConflict(reports);
        if (newlyRemoved) {
            message.setRemovedByStaff(true);
            chat.saveMessage(message);
            chat.broadcast(message);
        }

        activityLogService.log("CHAT_MESSAGE_REMOVED", staffUsername,
                "Removed a " + ChatMessageService.describe(channel) + " from " + message.getSenderUsername(), null);
        return toGroup(new MessageKey(channel, messageId), message, reports);
    }

    /** Only reported messages are moderated here; anything else is a 404. */
    private List<ChatMessageReport> reportsFor(ChatChannel channel, Long messageId) {
        List<ChatMessageReport> reports = reportRepo.findByChannelAndMessageIdOrderByReportedAtDesc(channel, messageId);
        if (reports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No reports for this message");
        }
        return reports;
    }

    /**
     * Flushes now, so if another staff member resolved these reports at the same moment this
     * surfaces as 409 instead of overwriting what they did.
     */
    private void saveOrConflict(List<ChatMessageReport> reports) {
        try {
            reportRepo.saveAllAndFlush(reports);
        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another staff member just reviewed this message; reload it");
        }
    }

    /** Reports on a channel a newer deploy added (read as null) can't be shown or acted on by this build. */
    private static List<ChatMessageReport> onKnownChannels(List<ChatMessageReport> reports) {
        return reports.stream().filter(r -> r.getChannel() != null).toList();
    }

    private static int resolveOpen(List<ChatMessageReport> reports, ReportStatus outcome, String staffUsername) {
        int closed = 0;
        for (ChatMessageReport report : reports) {
            if (report.resolve(outcome, staffUsername)) closed++;
        }
        return closed;
    }

    private static ChatReportGroupResponse toGroup(MessageKey key, ChatMessageBase message, List<ChatMessageReport> reports) {
        ChatReportGroupResponse g = new ChatReportGroupResponse();
        g.channel = key.channel().name();
        g.messageId = key.messageId();
        g.messageAvailable = message != null;
        if (message != null) {
            g.senderUsername = message.getSenderUsername();
            if (message instanceof DirectMessage dm) g.recipientUsername = dm.getRecipientUsername();
            // Staff see what was reported even if the sender deleted it since
            g.content = message.getContent();
            g.kind = message.getKind().name();
            g.media = ChatMessageResponse.Media.from(message.getMedia());
            g.sentAt = message.getSentAt().toString();
            g.deletedBySender = message.getDeletedAt() != null;
            g.removedByStaff = message.isRemovedByStaff();
        }
        g.reports = reports.stream()
                .sorted(Comparator.comparing(ChatMessageReport::getReportedAt).reversed())
                .map(ChatReportResponse::from)
                .collect(Collectors.toList());
        g.openCount = reports.stream().filter(r -> r.getStatus() == ReportStatus.OPEN).count();
        g.latestReportedAt = g.reports.isEmpty() ? null : g.reports.get(0).reportedAt;
        return g;
    }
}
