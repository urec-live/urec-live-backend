package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.AdminHelpRequestResponse;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.entity.HelpRequest;
import com.ureclive.urec_live_backend.entity.HelpRequestClosedBy;
import com.ureclive.urec_live_backend.entity.HelpRequestStatus;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.HelpRequestRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminHelpRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    @Mock private HelpRequestRepository helpRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private ActivityLogService activityLogService;

    @InjectMocks private AdminHelpRequestService service;

    private User member;
    private User staff;
    private Equipment bench;

    @BeforeEach
    void setUp() {
        member = new User("jdoe", "jdoe@example.com", "pw");
        member.setId(1L);
        staff = new User("coach", "coach@example.com", "pw");
        staff.setId(2L);
        bench = new Equipment("BP001", "Flat Bench Press 1", "Available", null);
        bench.setId(10L);
        bench.setFloorLabel("Zone A");
    }

    private HelpRequest request(long id, HelpRequestStatus status) {
        HelpRequest request = new HelpRequest(member, bench, new Exercise("Bench Press", "Chest", null));
        request.setId(id);
        request.setStatus(status);
        request.setCreatedAt(NOW);
        request.setUpdatedAt(NOW);
        return request;
    }

    private void staffExists() {
        when(userRepository.findByUsername("coach")).thenReturn(Optional.of(staff));
    }

    private void found(HelpRequest request) {
        when(helpRequestRepository.findWithDetailsById(request.getId())).thenReturn(Optional.of(request));
    }

    private void saveAndFlushReturnsArgument() {
        when(helpRequestRepository.saveAndFlush(any(HelpRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Queue and history ───────────────────────────────────────────────────

    @Test
    void getOpen_mapsTheQueueWithMemberMachineAndExercise() {
        HelpRequest waiting = request(1L, HelpRequestStatus.REQUEST_RECEIVED);
        HelpRequest coming = request(2L, HelpRequestStatus.ON_THE_WAY);
        coming.setStaffMember(staff);
        when(helpRequestRepository.findByStatusInOrderByCreatedAtAsc(HelpRequestStatus.OPEN))
                .thenReturn(List.of(waiting, coming));

        List<AdminHelpRequestResponse> open = service.getOpen();

        assertEquals(2, open.size());
        AdminHelpRequestResponse first = open.get(0);
        assertEquals(1L, first.getId());
        assertEquals("jdoe", first.getMemberUsername());
        assertEquals("Flat Bench Press 1", first.getEquipmentName());
        assertEquals("BP001", first.getEquipmentCode());
        assertEquals("Zone A", first.getFloorLabel());
        assertEquals("Bench Press", first.getExerciseName());
        assertNull(first.getStaffUsername());
        assertEquals("coach", open.get(1).getStaffUsername());
    }

    @ParameterizedTest(name = "limit {0} -> page size {1}")
    @CsvSource({"50, 50", "0, 1", "-5, 1", "200, 200", "5000, 200"})
    void getHistory_clampsTheLimit(int limit, int expectedSize) {
        when(helpRequestRepository.findByStatusInOrderByClosedAtDesc(eq(HelpRequestStatus.CLOSED), any(Pageable.class)))
                .thenReturn(List.of());

        service.getHistory(limit);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(helpRequestRepository).findByStatusInOrderByClosedAtDesc(eq(HelpRequestStatus.CLOSED), page.capture());
        assertEquals(0, page.getValue().getPageNumber());
        assertEquals(expectedSize, page.getValue().getPageSize());
    }

    // ── On the way / Too busy ───────────────────────────────────────────────

    @Test
    void setStatus_onTheWayRecordsTheStaffMemberAndLogsIt() {
        staffExists();
        found(request(5L, HelpRequestStatus.REQUEST_RECEIVED));
        saveAndFlushReturnsArgument();

        AdminHelpRequestResponse response = service.setStatus(5L, HelpRequestStatus.ON_THE_WAY, "coach");

        assertEquals(HelpRequestStatus.ON_THE_WAY, response.getStatus());
        assertEquals("coach", response.getStaffUsername());
        assertNotNull(response.getFirstResponseAt());
        verify(activityLogService).log("HELP_STATUS_CHANGED", "coach",
                "On the way to help jdoe at Flat Bench Press 1", "Flat Bench Press 1");
    }

    @Test
    void setStatus_tooBusyIsLoggedDifferently() {
        staffExists();
        found(request(5L, HelpRequestStatus.REQUEST_RECEIVED));
        saveAndFlushReturnsArgument();

        AdminHelpRequestResponse response = service.setStatus(5L, HelpRequestStatus.TOO_BUSY, "coach");

        assertEquals(HelpRequestStatus.TOO_BUSY, response.getStatus());
        verify(activityLogService).log("HELP_STATUS_CHANGED", "coach",
                "Too busy to help jdoe at Flat Bench Press 1 right now", "Flat Bench Press 1");
    }

    @Test
    void setStatus_switchingFromTooBusyToOnTheWayKeepsTheFirstResponseTime() {
        staffExists();
        HelpRequest busy = request(5L, HelpRequestStatus.TOO_BUSY);
        Instant firstResponse = NOW.plusSeconds(30);
        busy.setFirstResponseAt(firstResponse);
        found(busy);
        saveAndFlushReturnsArgument();

        AdminHelpRequestResponse response = service.setStatus(5L, HelpRequestStatus.ON_THE_WAY, "coach");

        assertEquals(HelpRequestStatus.ON_THE_WAY, response.getStatus());
        assertEquals(firstResponse, response.getFirstResponseAt());
    }

    @Test
    void setStatus_sameStatusIsANoOp() {
        staffExists();
        found(request(5L, HelpRequestStatus.ON_THE_WAY));

        AdminHelpRequestResponse response = service.setStatus(5L, HelpRequestStatus.ON_THE_WAY, "coach");

        assertEquals(HelpRequestStatus.ON_THE_WAY, response.getStatus());
        verify(helpRequestRepository, never()).saveAndFlush(any());
        verifyNoInteractions(activityLogService);
    }

    @ParameterizedTest(name = "staff can't set {0}")
    @EnumSource(value = HelpRequestStatus.class, names = {"REQUEST_RECEIVED", "RESOLVED", "CANCELLED", "EXPIRED"})
    void setStatus_rejectsStatusesStaffDontSet(HelpRequestStatus status) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.setStatus(5L, status, "coach"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(helpRequestRepository);
    }

    @Test
    void setStatus_rejectsANullStatus() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.setStatus(5L, null, "coach"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @ParameterizedTest(name = "a {0} request can't be reopened")
    @EnumSource(value = HelpRequestStatus.class, names = {"RESOLVED", "CANCELLED", "EXPIRED"})
    void setStatus_rejectsClosedRequests(HelpRequestStatus closed) {
        staffExists();
        found(request(5L, closed));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.setStatus(5L, HelpRequestStatus.ON_THE_WAY, "coach"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(helpRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void setStatus_unknownRequestIs404() {
        staffExists();
        when(helpRequestRepository.findWithDetailsById(404L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.setStatus(404L, HelpRequestStatus.ON_THE_WAY, "coach"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void setStatus_aConcurrentMemberCloseBecomes409() {
        staffExists();
        found(request(5L, HelpRequestStatus.REQUEST_RECEIVED));
        when(helpRequestRepository.saveAndFlush(any(HelpRequest.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(HelpRequest.class, 5L));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.setStatus(5L, HelpRequestStatus.ON_THE_WAY, "coach"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verifyNoInteractions(activityLogService);
    }

    // ── Done helping ────────────────────────────────────────────────────────

    @Test
    void markDone_resolvesAsClosedByStaff() {
        staffExists();
        found(request(5L, HelpRequestStatus.ON_THE_WAY));
        saveAndFlushReturnsArgument();

        AdminHelpRequestResponse response = service.markDone(5L, "coach");

        assertEquals(HelpRequestStatus.RESOLVED, response.getStatus());
        assertEquals(HelpRequestClosedBy.STAFF, response.getClosedBy());
        assertEquals("coach", response.getStaffUsername());
        assertNotNull(response.getClosedAt());
        verify(activityLogService).log("HELP_CLOSED", "coach", "Done helping jdoe at Flat Bench Press 1",
                "Flat Bench Press 1");
    }

    @Test
    void markDone_worksStraightFromRequestReceived() {
        staffExists();
        found(request(5L, HelpRequestStatus.REQUEST_RECEIVED));
        saveAndFlushReturnsArgument();

        assertEquals(HelpRequestStatus.RESOLVED, service.markDone(5L, "coach").getStatus());
    }

    @Test
    void markDone_rejectsAnAlreadyClosedRequest() {
        staffExists();
        found(request(5L, HelpRequestStatus.CANCELLED));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.markDone(5L, "coach"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verifyNoInteractions(activityLogService);
    }

    // ── Expiry ──────────────────────────────────────────────────────────────

    @Test
    void expireIdleBefore_closesEveryIdleOpenRequestAndLogsEach() {
        Instant cutoff = NOW.minusSeconds(1800);
        HelpRequest stale = request(5L, HelpRequestStatus.REQUEST_RECEIVED);
        HelpRequest staleBusy = request(6L, HelpRequestStatus.TOO_BUSY);
        when(helpRequestRepository.findByStatusInAndUpdatedAtBefore(HelpRequestStatus.OPEN, cutoff))
                .thenReturn(List.of(stale, staleBusy));

        int expired = service.expireIdleBefore(cutoff);

        assertEquals(2, expired);
        for (HelpRequest request : List.of(stale, staleBusy)) {
            assertEquals(HelpRequestStatus.EXPIRED, request.getStatus());
            assertEquals(HelpRequestClosedBy.SYSTEM, request.getClosedBy());
            assertNotNull(request.getClosedAt());
        }
        verify(helpRequestRepository).saveAll(List.of(stale, staleBusy));
        verify(activityLogService, times(2)).log("HELP_CLOSED", "system",
                "Help request from jdoe at Flat Bench Press 1 expired", "Flat Bench Press 1");
    }

    @Test
    void expireIdleBefore_doesNothingWhenNothingIsIdle() {
        Instant cutoff = NOW.minusSeconds(1800);
        when(helpRequestRepository.findByStatusInAndUpdatedAtBefore(HelpRequestStatus.OPEN, cutoff))
                .thenReturn(List.of());

        assertEquals(0, service.expireIdleBefore(cutoff));
        verifyNoInteractions(activityLogService);
    }
}
