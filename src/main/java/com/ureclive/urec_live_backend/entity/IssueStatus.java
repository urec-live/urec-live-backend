package com.ureclive.urec_live_backend.entity;

/**
 * Lifecycle of an equipment issue report, declared in the order staff move it along.
 * Natural (ordinal) order is used to pick the furthest-along status.
 */
public enum IssueStatus {
    /** Submitted by a member, not yet reviewed by staff. */
    REPORTED,
    /** Staff have seen the report. */
    ACKNOWLEDGED,
    /** A repair is on the way. */
    IN_PROGRESS,
    /** Fixed. */
    RESOLVED
}
