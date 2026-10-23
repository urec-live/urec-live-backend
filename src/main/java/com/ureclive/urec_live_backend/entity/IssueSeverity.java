package com.ureclive.urec_live_backend.entity;

/**
 * How badly a reported machine is affected.
 * Declared worst-first: natural (ordinal) order is used to pick the worst severity.
 */
public enum IssueSeverity {
    /** Can't be used at all — won't move, weights don't engage, etc. */
    OUT_OF_ORDER,
    /** Still usable but damaged or uncomfortable — torn padding, damaged seat, loose parts. */
    DAMAGED
}
