package com.ureclive.urec_live_backend.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class HelpRequestTest {

    private static final Instant T0 = Instant.parse("2026-10-04T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final Instant T2 = T0.plusSeconds(120);

    private User staff;
    private User otherStaff;
    private HelpRequest request;

    @BeforeEach
    void setUp() {
        staff = new User("coach", "coach@example.com", "pw");
        otherStaff = new User("coach2", "coach2@example.com", "pw");
        request = new HelpRequest(new User("jdoe", "jdoe@example.com", "pw"),
                new Equipment("BP001", "Flat Bench Press 1", "Available", null), null);
        request.setCreatedAt(T0);
        request.setUpdatedAt(T0);
    }

    // ── Status sets ─────────────────────────────────────────────────────────

    @Test
    void onlyTheFirstThreeStatusesAreOpen() {
        assertTrue(HelpRequestStatus.REQUEST_RECEIVED.isOpen());
        assertTrue(HelpRequestStatus.ON_THE_WAY.isOpen());
        assertTrue(HelpRequestStatus.TOO_BUSY.isOpen());
        assertFalse(HelpRequestStatus.RESOLVED.isOpen());
        assertFalse(HelpRequestStatus.CANCELLED.isOpen());
        assertFalse(HelpRequestStatus.EXPIRED.isOpen());
        assertEquals(HelpRequestStatus.values().length,
                HelpRequestStatus.OPEN.size() + HelpRequestStatus.CLOSED.size());
    }

    @Test
    void onlyOnTheWayAndTooBusyAreStaffResponses() {
        for (HelpRequestStatus status : HelpRequestStatus.values()) {
            boolean expected = status == HelpRequestStatus.ON_THE_WAY || status == HelpRequestStatus.TOO_BUSY;
            assertEquals(expected, status.isStaffResponse(), status.name());
        }
    }

    @Test
    void newRequestStartsAsRequestReceived() {
        assertEquals(HelpRequestStatus.REQUEST_RECEIVED, request.getStatus());
        assertTrue(request.isOpen());
        assertNull(request.getStaffMember());
        assertNull(request.getFirstResponseAt());
    }

    // ── Staff responses ─────────────────────────────────────────────────────

    @Test
    void respondRecordsStaffAndFirstResponseTime() {
        assertTrue(request.respond(staff, HelpRequestStatus.ON_THE_WAY, T1));

        assertEquals(HelpRequestStatus.ON_THE_WAY, request.getStatus());
        assertSame(staff, request.getStaffMember());
        assertEquals(T1, request.getFirstResponseAt());
        assertEquals(T1, request.getUpdatedAt());
    }

    @Test
    void laterResponsesKeepTheFirstResponseTimeButUpdateTheStaffMember() {
        request.respond(staff, HelpRequestStatus.TOO_BUSY, T1);
        assertTrue(request.respond(otherStaff, HelpRequestStatus.ON_THE_WAY, T2));

        assertEquals(HelpRequestStatus.ON_THE_WAY, request.getStatus());
        assertSame(otherStaff, request.getStaffMember());
        assertEquals(T1, request.getFirstResponseAt());
        assertEquals(T2, request.getUpdatedAt());
    }

    @Test
    void respondingWithTheCurrentStatusChangesNothing() {
        request.respond(staff, HelpRequestStatus.ON_THE_WAY, T1);
        assertFalse(request.respond(otherStaff, HelpRequestStatus.ON_THE_WAY, T2));

        assertSame(staff, request.getStaffMember());
        assertEquals(T1, request.getUpdatedAt());
    }

    @ParameterizedTest(name = "{0} is not a staff response")
    @EnumSource(value = HelpRequestStatus.class, names = {"REQUEST_RECEIVED", "RESOLVED", "CANCELLED", "EXPIRED"})
    void respondRejectsNonStaffStatuses(HelpRequestStatus status) {
        assertThrows(IllegalArgumentException.class, () -> request.respond(staff, status, T1));
        assertEquals(HelpRequestStatus.REQUEST_RECEIVED, request.getStatus());
    }

    // ── Closing ─────────────────────────────────────────────────────────────

    @Test
    void staffClosingRecordsWhoAndWhen() {
        request.close(HelpRequestStatus.RESOLVED, HelpRequestClosedBy.STAFF, staff, T1);

        assertEquals(HelpRequestStatus.RESOLVED, request.getStatus());
        assertEquals(HelpRequestClosedBy.STAFF, request.getClosedBy());
        assertSame(staff, request.getStaffMember());
        assertEquals(T1, request.getClosedAt());
        assertEquals(T1, request.getUpdatedAt());
        // Coming straight over without clicking "On the way" still counts as the first response
        assertEquals(T1, request.getFirstResponseAt());
        assertFalse(request.isOpen());
    }

    @Test
    void memberClosingKeepsTheStaffMemberWhoResponded() {
        request.respond(staff, HelpRequestStatus.ON_THE_WAY, T1);
        request.close(HelpRequestStatus.RESOLVED, HelpRequestClosedBy.MEMBER, null, T2);

        assertEquals(HelpRequestClosedBy.MEMBER, request.getClosedBy());
        assertSame(staff, request.getStaffMember());
        assertEquals(T1, request.getFirstResponseAt());
        assertEquals(T2, request.getClosedAt());
    }

    @Test
    void memberClosingAnUnansweredRequestLeavesNoResponseTime() {
        request.close(HelpRequestStatus.CANCELLED, HelpRequestClosedBy.MEMBER, null, T1);

        assertNull(request.getStaffMember());
        assertNull(request.getFirstResponseAt());
    }

    @ParameterizedTest(name = "{0} is not a closing status")
    @EnumSource(value = HelpRequestStatus.class, names = {"REQUEST_RECEIVED", "ON_THE_WAY", "TOO_BUSY"})
    void closeRejectsOpenStatuses(HelpRequestStatus status) {
        assertThrows(IllegalArgumentException.class,
                () -> request.close(status, HelpRequestClosedBy.STAFF, staff, T1));
    }

    @ParameterizedTest(name = "a {0} request is final")
    @EnumSource(value = HelpRequestStatus.class, names = {"RESOLVED", "CANCELLED", "EXPIRED"})
    void closedRequestsCannotBeRespondedToOrClosedAgain(HelpRequestStatus finalStatus) {
        request.close(finalStatus, HelpRequestClosedBy.SYSTEM, null, T1);

        assertThrows(IllegalStateException.class, () -> request.respond(staff, HelpRequestStatus.ON_THE_WAY, T2));
        assertThrows(IllegalStateException.class,
                () -> request.close(HelpRequestStatus.RESOLVED, HelpRequestClosedBy.STAFF, staff, T2));
        assertEquals(finalStatus, request.getStatus());
        assertEquals(T1, request.getClosedAt());
    }
}
