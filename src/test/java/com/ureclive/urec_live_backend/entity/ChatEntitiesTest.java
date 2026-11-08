package com.ureclive.urec_live_backend.entity;

import com.ureclive.urec_live_backend.dto.ChatMessageResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatEntitiesTest {

    @Test
    void onlyQuickReactionsNormalize() {
        assertEquals("👍", MessageReaction.normalizeEmoji(" 👍 "));
        assertEquals("❤️", MessageReaction.normalizeEmoji("❤"));
        assertEquals("❤️", MessageReaction.normalizeEmoji("❤️"));
        assertNull(MessageReaction.normalizeEmoji("🍕"));
        assertNull(MessageReaction.normalizeEmoji("👍👍"));
        assertNull(MessageReaction.normalizeEmoji(""));
        assertNull(MessageReaction.normalizeEmoji(null));
        assertEquals(6, MessageReaction.ALLOWED_EMOJI.size());
    }

    @Test
    void reportsResolveOnceAndNeverBackToOpen() {
        ChatMessageReport report = new ChatMessageReport(ChatChannel.COMMUNITY, 1L, "bob", ReportReason.SPAM, null);
        assertFalse(report.resolve(ReportStatus.OPEN, "staff"));
        assertTrue(report.resolve(ReportStatus.DISMISSED, "staff"));
        assertEquals(ReportStatus.DISMISSED, report.getStatus());
        assertEquals("staff", report.getResolvedBy());
        assertNotNull(report.getResolvedAt());
        assertFalse(report.resolve(ReportStatus.ACTIONED, "other"));
        assertEquals("staff", report.getResolvedBy());
    }

    @Test
    void convertersStoreNamesAndReadMissingKindsAsText() {
        MessageKindConverter kinds = new MessageKindConverter();
        assertEquals("GIF", kinds.convertToDatabaseColumn(MessageKind.GIF));
        assertEquals(MessageKind.STICKER, kinds.convertToEntityAttribute("STICKER"));
        assertEquals(MessageKind.TEXT, kinds.convertToEntityAttribute(null));
        assertNull(kinds.convertToDatabaseColumn(null));
        assertEquals(ChatChannel.DM, new ChatChannelConverter().convertToEntityAttribute("DM"));
    }

    @Test
    void namesFromANewerDeployReadLikeMissingOnesInsteadOfThrowing() {
        assertEquals(MessageKind.TEXT, new MessageKindConverter().convertToEntityAttribute("PHOTO"));
        assertEquals(MessageKind.TEXT, new MessageKindConverter().convertToEntityAttribute(""));
        assertNull(new ChatChannelConverter().convertToEntityAttribute("GROUP"));
        assertEquals(ReportReason.OTHER, new ReportEnumConverters.ReasonConverter().convertToEntityAttribute("SCAM"));
        assertEquals(ReportStatus.OPEN, new ReportEnumConverters.StatusConverter().convertToEntityAttribute("ESCALATED"));
        assertEquals(ReportStatus.ACTIONED, new ReportEnumConverters.StatusConverter().convertToEntityAttribute("ACTIONED"));
    }

    @Test
    void channelsResolveFromUrlSegments() {
        assertEquals(ChatChannel.COMMUNITY, ChatChannel.fromPath("community"));
        assertEquals(ChatChannel.DM, ChatChannel.fromPath("DM"));
        assertEquals("dm", ChatChannel.DM.path());
    }

    @Test
    void hiddenMessagesKeepTheirPlaceButNotTheirContent() {
        CommunityMessage message = new CommunityMessage();
        message.setSenderUsername("alice");
        message.setContent("secret");
        message.setKind(MessageKind.GIF);
        message.setMedia(new MessageMedia("https://static.klipy.com/a.webp", null, 10, 10, "a"));
        message.onSend();

        ChatMessageResponse visible = ChatMessageResponse.from(message, Map.of("🔥", List.of("bob")));
        assertEquals("secret", visible.content);
        assertNotNull(visible.media);
        assertEquals(List.of("bob"), visible.reactions.get("🔥"));

        message.setDeletedAt(Instant.now());
        ChatMessageResponse deleted = ChatMessageResponse.from(message, Map.of("🔥", List.of("bob")));
        assertTrue(deleted.deleted);
        assertEquals("", deleted.content);
        assertNull(deleted.media);
        assertTrue(deleted.reactions.isEmpty());
        assertEquals("GIF", deleted.kind);

        message.setDeletedAt(null);
        message.setRemovedByStaff(true);
        ChatMessageResponse removed = ChatMessageResponse.from(message);
        assertTrue(removed.removedByStaff);
        assertFalse(removed.deleted);
        assertEquals("", removed.content);
    }

    @Test
    void directMessagesKnowWhoIsInvolved() {
        DirectMessage dm = new DirectMessage();
        dm.setSenderUsername("alice");
        dm.setRecipientUsername("bob");
        assertTrue(dm.involves("alice"));
        assertTrue(dm.involves("bob"));
        assertFalse(dm.involves("carol"));
        assertEquals(ChatChannel.DM, dm.getChannel());
    }

    @Test
    void oldRowsWithoutAKindReadAsText() {
        CommunityMessage message = new CommunityMessage();
        message.setKind(null);
        assertEquals(MessageKind.TEXT, message.getKind());
        assertFalse(MessageKind.TEXT.hasMedia());
        assertTrue(MessageKind.STICKER.hasMedia());
    }
}
