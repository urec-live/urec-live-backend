package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.CreateIssueReportRequest;
import com.ureclive.urec_live_backend.dto.IssueReportResponse;
import com.ureclive.urec_live_backend.dto.MachineIssueStatusResponse;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentIssueReport;
import com.ureclive.urec_live_backend.entity.IssueSeverity;
import com.ureclive.urec_live_backend.entity.IssueStatus;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentIssueReportRepository;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentIssueServiceTest {

    @Mock private EquipmentIssueReportRepository issueReportRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private ActivityLogService activityLogService;

    @InjectMocks private EquipmentIssueService service;

    private User member;
    private Equipment legPress;

    @BeforeEach
    void setUp() {
        member = new User("jdoe", "jdoe@example.com", "hashed");
        member.setId(1L);
        legPress = new Equipment("LP01", "Leg Press", "Available", null);
        legPress.setId(10L);
    }

    @Test
    void createReport_savesTrimmedDescriptionAndLogsActivity() {
        givenMemberAndMachine();
        when(issueReportRepository.existsByReporterAndEquipmentAndStatusNot(member, legPress, IssueStatus.RESOLVED))
                .thenReturn(false);
        when(issueReportRepository.save(any(EquipmentIssueReport.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueReportResponse response = service.createReport(
                request(IssueSeverity.OUT_OF_ORDER, "  Cable snapped, weights won't lift  "), "jdoe");

        assertEquals("Cable snapped, weights won't lift", response.getDescription());
        assertEquals(IssueSeverity.OUT_OF_ORDER, response.getSeverity());
        assertEquals(IssueStatus.REPORTED, response.getStatus());
        assertEquals(10L, response.getEquipmentId());
        assertEquals("jdoe", response.getReporterUsername());
        verify(activityLogService).log("ISSUE_REPORTED", "jdoe", "Reported Leg Press as not working", "Leg Press");
    }

    @Test
    void createReport_rejectsSecondOpenReportOnSameMachine() {
        givenMemberAndMachine();
        when(issueReportRepository.existsByReporterAndEquipmentAndStatusNot(member, legPress, IssueStatus.RESOLVED))
                .thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createReport(request(IssueSeverity.DAMAGED, "Seat padding is torn open"), "jdoe"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(issueReportRepository, never()).save(any());
    }

    @Test
    void createReport_rejectsRemovedMachine() {
        legPress.setDeleted(true);
        givenMemberAndMachine();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createReport(request(IssueSeverity.DAMAGED, "Seat padding is torn open"), "jdoe"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(issueReportRepository, never()).save(any());
    }

    @Test
    void createReport_rejectsDescriptionTooShortOnceTrimmed() {
        givenMemberAndMachine();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createReport(request(IssueSeverity.DAMAGED, "    broken    "), "jdoe"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(issueReportRepository, never()).save(any());
    }

    // ── Withdrawing a report filed by mistake ─────────────────────────────────

    @Test
    void withdrawReport_closesTheMembersOwnOpenReportAndLogsIt() {
        EquipmentIssueReport report = report(IssueSeverity.OUT_OF_ORDER, IssueStatus.ACKNOWLEDGED);
        when(issueReportRepository.findById(5L)).thenReturn(Optional.of(report));
        when(issueReportRepository.save(report)).thenReturn(report);

        IssueReportResponse response = service.withdrawReport(5L, "jdoe");

        assertEquals(IssueStatus.RESOLVED, response.getStatus());
        assertNotNull(response.getWithdrawnAt());
        assertNotNull(response.getResolvedAt());
        verify(issueReportRepository).save(report);
        verify(activityLogService).log("ISSUE_WITHDRAWN", "jdoe",
                "Withdrew their report on Leg Press (reported by mistake)", "Leg Press");
    }

    @Test
    void withdrawReport_onSomeoneElsesReportIsNotFound() {
        when(issueReportRepository.findById(5L))
                .thenReturn(Optional.of(report(IssueSeverity.DAMAGED, IssueStatus.REPORTED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.withdrawReport(5L, "someone_else"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(issueReportRepository, never()).save(any());
        verifyNoInteractions(activityLogService);
    }

    @Test
    void withdrawReport_onAnUnknownReportIsNotFound() {
        when(issueReportRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.withdrawReport(99L, "jdoe"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void withdrawReport_onAClosedReportIsAConflict() {
        EquipmentIssueReport fixed = report(IssueSeverity.DAMAGED, IssueStatus.RESOLVED);
        EquipmentIssueReport withdrawn = report(IssueSeverity.DAMAGED, IssueStatus.REPORTED);
        withdrawn.withdraw();
        when(issueReportRepository.findById(5L)).thenReturn(Optional.of(fixed));
        when(issueReportRepository.findById(6L)).thenReturn(Optional.of(withdrawn));

        ResponseStatusException fixedEx = assertThrows(ResponseStatusException.class,
                () -> service.withdrawReport(5L, "jdoe"));
        ResponseStatusException withdrawnEx = assertThrows(ResponseStatusException.class,
                () -> service.withdrawReport(6L, "jdoe"));

        assertEquals(HttpStatus.CONFLICT, fixedEx.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, withdrawnEx.getStatusCode());
        assertNull(fixed.getWithdrawnAt()); // a fixed report doesn't turn into a withdrawn one
        verify(issueReportRepository, never()).save(any());
        verifyNoInteractions(activityLogService);
    }

    @Test
    void getMachineIssueStatus_reportsWorstSeverityAndFurthestStatus() {
        when(issueReportRepository.findByEquipmentIdAndStatusNotOrderByReportedAtDesc(10L, IssueStatus.RESOLVED))
                .thenReturn(List.of(
                        report(IssueSeverity.DAMAGED, IssueStatus.IN_PROGRESS),
                        report(IssueSeverity.OUT_OF_ORDER, IssueStatus.REPORTED)));

        MachineIssueStatusResponse status = service.getMachineIssueStatus(10L);

        assertEquals(2, status.getOpenReportCount());
        assertEquals(IssueSeverity.OUT_OF_ORDER, status.getWorstSeverity());
        assertEquals(IssueStatus.IN_PROGRESS, status.getStatus());
    }

    @Test
    void getMachineIssueStatus_isEmptyWhenNothingIsOpen() {
        when(issueReportRepository.findByEquipmentIdAndStatusNotOrderByReportedAtDesc(10L, IssueStatus.RESOLVED))
                .thenReturn(List.of());

        MachineIssueStatusResponse status = service.getMachineIssueStatus(10L);

        assertEquals(0, status.getOpenReportCount());
        assertNull(status.getWorstSeverity());
        assertNull(status.getStatus());
    }

    private void givenMemberAndMachine() {
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(member));
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(legPress));
    }

    private CreateIssueReportRequest request(IssueSeverity severity, String description) {
        CreateIssueReportRequest request = new CreateIssueReportRequest();
        request.setEquipmentId(10L);
        request.setSeverity(severity);
        request.setDescription(description);
        return request;
    }

    private EquipmentIssueReport report(IssueSeverity severity, IssueStatus status) {
        EquipmentIssueReport report = new EquipmentIssueReport(legPress, member, severity, "Something is wrong here");
        report.setStatus(status);
        return report;
    }
}
