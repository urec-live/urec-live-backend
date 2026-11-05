package com.ureclive.urec_live_backend.entity;

/** Where a chat message report stands in the staff review queue. */
public enum ReportStatus {
    /** Waiting for staff. */
    OPEN,
    /** Staff looked and left the message up. */
    DISMISSED,
    /** Staff removed the message. */
    ACTIONED
}
