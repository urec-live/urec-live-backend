package com.ureclive.urec_live_backend.dto;

import java.util.List;

/**
 * A reported chat message with all its reports, for the admin queue. Shows the original content
 * and media even if the sender has since deleted it, so staff can still judge the report.
 */
public class ChatReportGroupResponse {
    /** "COMMUNITY" or "DM". */
    public String channel;
    public Long messageId;
    public String senderUsername;
    /** DMs only. */
    public String recipientUsername;
    public String content;
    public String kind;
    public ChatMessageResponse.Media media;
    public String sentAt;
    /** The sender deleted it; null if the message itself no longer exists. */
    public Boolean deletedBySender;
    public Boolean removedByStaff;
    /** False when the reported message can't be found any more. */
    public boolean messageAvailable;
    public long openCount;
    public String latestReportedAt;
    /** Newest first. */
    public List<ChatReportResponse> reports;
}
