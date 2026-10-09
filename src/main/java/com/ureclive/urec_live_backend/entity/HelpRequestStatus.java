package com.ureclive.urec_live_backend.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a member's "call staff" help request. The first three statuses are open
 * (a member can have only one open request); the rest are final.
 */
public enum HelpRequestStatus {
    /** The member pressed "Call staff" and no staff member has responded yet. */
    REQUEST_RECEIVED,
    /** A staff member is heading over. */
    ON_THE_WAY,
    /** Staff saw the request but can't come right now. It stays open. */
    TOO_BUSY,
    /** Staff clicked "Done helping", or the member confirmed "Received help". */
    RESOLVED,
    /** The member withdrew the request. */
    CANCELLED,
    /** Nobody updated the request for too long, so the system closed it. */
    EXPIRED;

    public static final Set<HelpRequestStatus> OPEN =
            Collections.unmodifiableSet(EnumSet.of(REQUEST_RECEIVED, ON_THE_WAY, TOO_BUSY));
    public static final Set<HelpRequestStatus> CLOSED =
            Collections.unmodifiableSet(EnumSet.of(RESOLVED, CANCELLED, EXPIRED));

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    /** The statuses staff set from the dashboard: "On the way" and "Too busy". */
    public boolean isStaffResponse() {
        return this == ON_THE_WAY || this == TOO_BUSY;
    }
}
