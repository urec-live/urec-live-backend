package com.ureclive.urec_live_backend.dto;

import java.time.Instant;
import java.util.List;

public class DataQualityAudit {
    private final Instant windowStart;
    private final int sessionsMissingEvents;
    private final List<Long> sessionIdsMissingEvents;
    private final int activeSessionsWithEndedAt;
    private final int endedSessionsMissingEndedAt;
    private final int endedSessionsMissingEndReason;
    private final int staleActiveSessions;
    private final List<Long> staleActiveSessionIds;
    private final int sessionsWithDuplicateStarts;
    private final List<Long> sessionIdsWithDuplicateStarts;
    private final int sessionsWithDuplicateTerminalEvents;
    private final List<Long> sessionIdsWithDuplicateTerminalEvents;
    private final int sessionsWithInvalidTerminalEvent;
    private final List<Long> sessionIdsWithInvalidTerminalEvent;
    private final int sessionsWithOutOfOrderEvents;
    private final List<Long> sessionIdsWithOutOfOrderEvents;

    public DataQualityAudit(
            Instant windowStart,
            int sessionsMissingEvents,
            List<Long> sessionIdsMissingEvents,
            int activeSessionsWithEndedAt,
            int endedSessionsMissingEndedAt,
            int endedSessionsMissingEndReason,
            int staleActiveSessions,
            List<Long> staleActiveSessionIds,
            int sessionsWithDuplicateStarts,
            List<Long> sessionIdsWithDuplicateStarts,
            int sessionsWithDuplicateTerminalEvents,
            List<Long> sessionIdsWithDuplicateTerminalEvents,
            int sessionsWithInvalidTerminalEvent,
            List<Long> sessionIdsWithInvalidTerminalEvent,
            int sessionsWithOutOfOrderEvents,
            List<Long> sessionIdsWithOutOfOrderEvents
    ) {
        this.windowStart = windowStart;
        this.sessionsMissingEvents = sessionsMissingEvents;
        this.sessionIdsMissingEvents = sessionIdsMissingEvents;
        this.activeSessionsWithEndedAt = activeSessionsWithEndedAt;
        this.endedSessionsMissingEndedAt = endedSessionsMissingEndedAt;
        this.endedSessionsMissingEndReason = endedSessionsMissingEndReason;
        this.staleActiveSessions = staleActiveSessions;
        this.staleActiveSessionIds = staleActiveSessionIds;
        this.sessionsWithDuplicateStarts = sessionsWithDuplicateStarts;
        this.sessionIdsWithDuplicateStarts = sessionIdsWithDuplicateStarts;
        this.sessionsWithDuplicateTerminalEvents = sessionsWithDuplicateTerminalEvents;
        this.sessionIdsWithDuplicateTerminalEvents = sessionIdsWithDuplicateTerminalEvents;
        this.sessionsWithInvalidTerminalEvent = sessionsWithInvalidTerminalEvent;
        this.sessionIdsWithInvalidTerminalEvent = sessionIdsWithInvalidTerminalEvent;
        this.sessionsWithOutOfOrderEvents = sessionsWithOutOfOrderEvents;
        this.sessionIdsWithOutOfOrderEvents = sessionIdsWithOutOfOrderEvents;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public int getSessionsMissingEvents() {
        return sessionsMissingEvents;
    }

    public List<Long> getSessionIdsMissingEvents() {
        return sessionIdsMissingEvents;
    }

    public int getActiveSessionsWithEndedAt() {
        return activeSessionsWithEndedAt;
    }

    public int getEndedSessionsMissingEndedAt() {
        return endedSessionsMissingEndedAt;
    }

    public int getEndedSessionsMissingEndReason() {
        return endedSessionsMissingEndReason;
    }

    public int getStaleActiveSessions() {
        return staleActiveSessions;
    }

    public List<Long> getStaleActiveSessionIds() {
        return staleActiveSessionIds;
    }

    public int getSessionsWithDuplicateStarts() {
        return sessionsWithDuplicateStarts;
    }

    public List<Long> getSessionIdsWithDuplicateStarts() {
        return sessionIdsWithDuplicateStarts;
    }

    public int getSessionsWithDuplicateTerminalEvents() {
        return sessionsWithDuplicateTerminalEvents;
    }

    public List<Long> getSessionIdsWithDuplicateTerminalEvents() {
        return sessionIdsWithDuplicateTerminalEvents;
    }

    public int getSessionsWithInvalidTerminalEvent() {
        return sessionsWithInvalidTerminalEvent;
    }

    public List<Long> getSessionIdsWithInvalidTerminalEvent() {
        return sessionIdsWithInvalidTerminalEvent;
    }

    public int getSessionsWithOutOfOrderEvents() {
        return sessionsWithOutOfOrderEvents;
    }

    public List<Long> getSessionIdsWithOutOfOrderEvents() {
        return sessionIdsWithOutOfOrderEvents;
    }
}
