package com.ureclive.urec_live_backend.entity;

/** Who closed a help request. */
public enum HelpRequestClosedBy {
    /** A staff member clicked "Done helping". */
    STAFF,
    /** The member confirmed "Received help" or cancelled the request. */
    MEMBER,
    /** The expiry job closed an idle request. */
    SYSTEM
}
