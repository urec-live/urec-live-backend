package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.AdminEquipmentResponse;
import com.ureclive.urec_live_backend.dto.EquipmentIssueGroupResponse;
import com.ureclive.urec_live_backend.dto.IssueReportResponse;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentIssueReport;
import com.ureclive.urec_live_backend.entity.EquipmentStatuses;
import com.ureclive.urec_live_backend.entity.IssueSeverity;
import com.ureclive.urec_live_backend.entity.IssueStatus;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentIssueReportRepository;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
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
class AdminEquipmentIssueServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    @Mock private EquipmentIssueReportRepository issueReportRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private ActivityLogService activityLogService;
    @Mock private MachineStatusService machineStatusService;

    @InjectMocks private AdminEquipmentIssueService service;

    private final User member = new User("jdoe", "jdoe@example.com", "hashed");
    private final Equipment legPress = machine(10L, "Leg Press");
    private final Equipment bench = machine(11L, "Bench Press");
    private final Equipment treadmill = machine(12L, "Treadmill");

    @Test
    void updateStatus_setsResolvedAtOnlyWhileResolved() {
        EquipmentIssueReport report = report(1L, legPress, IssueSeverity.DAMAGED, IssueStatus.ACKNOWLEDGED, NOW);
        when(issueReportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(issueReportRepository.save(report)).thenReturn(report);

        IssueReportResponse resolved = service.updateStatus(1L, IssueStatus.RESOLVED, "admin");
        assertEquals(IssueStatus.RESOLVED, resolved.getStatus());
        assertNotNull(resolved.getResolvedAt());

        IssueReportResponse reopened = service.updateStatus(1L, IssueStatus.IN_PROGRESS, "admin");
        assertEquals(IssueStatus.IN_PROGRESS, reopened.getStatus());
        assertNull(reopened.getResolvedAt());

        verify(activityLogService).log("ISSUE_STATUS_CHANGED", "admin",
                "Report #1 on Leg Press marked resolved", "Leg Press");
        verify(activityLogService).log("ISSUE_STATUS_CHANGED", "admin",
                "Report #1 on Leg Press marked in progress", "Leg Press");
    }

    @Test
    void updateStatus_doesNothingWhenStatusIsUnchanged() {
        EquipmentIssueReport report = report(1L, legPress, IssueSeverity.DAMAGED, IssueStatus.ACKNOWLEDGED, NOW);
        when(issueReportRepository.findById(1L)).thenReturn(Optional.of(report));

        service.updateStatus(1L, IssueStatus.ACKNOWLEDGED, "admin");

        verify(issueReportRepository, never()).save(any());
        verifyNoInteractions(activityLogService);
    }

    @Test
    void updateStatus_unknownReportIsNotFound() {
        when(issueReportRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateStatus(99L, IssueStatus.ACKNOWLEDGED, "admin"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getGrouped_listsNotWorkingMachinesFirstThenMostRecent() {
        // Repository returns newest first
        when(issueReportRepository.findByStatusNotAndEquipmentDeletedFalseOrderByReportedAtDesc(IssueStatus.RESOLVED))
                .thenReturn(List.of(
                        report(1L, bench, IssueSeverity.DAMAGED, IssueStatus.REPORTED, NOW),
                        report(2L, treadmill, IssueSeverity.DAMAGED, IssueStatus.REPORTED, NOW.minusSeconds(60)),
                        report(3L, legPress, IssueSeverity.OUT_OF_ORDER, IssueStatus.ACKNOWLEDGED, NOW.minusSeconds(120)),
                        report(4L, bench, IssueSeverity.DAMAGED, IssueStatus.REPORTED, NOW.minusSeconds(180))));

        List<EquipmentIssueGroupResponse> groups = service.getGrouped(false);

        assertEquals(List.of("Leg Press", "Bench Press", "Treadmill"),
                groups.stream().map(EquipmentIssueGroupResponse::getEquipmentName).toList());
        EquipmentIssueGroupResponse benchGroup = groups.get(1);
        assertEquals(2, benchGroup.getOpenReportCount());
        assertEquals(List.of(1L, 4L), benchGroup.getReports().stream().map(IssueReportResponse::getId).toList());
        assertEquals(NOW, benchGroup.getLatestReportedAt());
    }

    @Test
    void getGrouped_withResolvedPutsFullyResolvedMachinesLast() {
        when(issueReportRepository.findByEquipmentDeletedFalseOrderByReportedAtDesc())
                .thenReturn(List.of(
                        report(1L, treadmill, IssueSeverity.OUT_OF_ORDER, IssueStatus.RESOLVED, NOW),
                        report(2L, bench, IssueSeverity.DAMAGED, IssueStatus.REPORTED, NOW.minusSeconds(60))));

        List<EquipmentIssueGroupResponse> groups = service.getGrouped(true);

        assertEquals(List.of("Bench Press", "Treadmill"),
                groups.stream().map(EquipmentIssueGroupResponse::getEquipmentName).toList());
        assertEquals(0, groups.get(1).getOpenReportCount());
        assertNull(groups.get(1).getWorstSeverity());
    }

    @Test
    void updateStatusForEquipment_updatesEveryOpenReport() {
        EquipmentIssueReport first = report(1L, legPress, IssueSeverity.OUT_OF_ORDER, IssueStatus.REPORTED, NOW);
        EquipmentIssueReport second = report(2L, legPress, IssueSeverity.DAMAGED, IssueStatus.ACKNOWLEDGED, NOW.minusSeconds(60));
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(legPress));
        when(issueReportRepository.findByEquipmentIdAndStatusNotOrderByReportedAtDesc(10L, IssueStatus.RESOLVED))
                .thenReturn(List.of(first, second));

        EquipmentIssueGroupResponse group = service.updateStatusForEquipment(10L, IssueStatus.IN_PROGRESS, "admin");

        assertEquals(IssueStatus.IN_PROGRESS, first.getStatus());
        assertEquals(IssueStatus.IN_PROGRESS, second.getStatus());
        assertEquals(2, group.getOpenReportCount());
        verify(issueReportRepository).saveAll(List.of(first, second));
        verify(activityLogService).log("ISSUE_STATUS_CHANGED", "admin",
                "2 report(s) on Leg Press marked in progress", "Leg Press");
    }

    @Test
    void updateStatusForEquipment_withNoOpenReportsIsNotFound() {
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(legPress));
        when(issueReportRepository.findByEquipmentIdAndStatusNotOrderByReportedAtDesc(10L, IssueStatus.RESOLVED))
                .thenReturn(List.of());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateStatusForEquipment(10L, IssueStatus.RESOLVED, "admin"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(activityLogService);
    }

    // ── Out of order ──────────────────────────────────────────────────────────

    @Test
    void resolvingTheLastOpenReportPutsAnOutOfOrderMachineBackInService() {
        legPress.setStatus(EquipmentStatuses.OUT_OF_ORDER);
        EquipmentIssueReport report = report(1L, legPress, IssueSeverity.OUT_OF_ORDER, IssueStatus.IN_PROGRESS, NOW);
        when(issueReportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(issueReportRepository.save(report)).thenReturn(report);
        when(issueReportRepository.existsByEquipmentIdAndStatusNot(10L, IssueStatus.RESOLVED)).thenReturn(false);

        service.updateStatus(1L, IssueStatus.RESOLVED, "admin");

        verify(machineStatusService).changeStatus(legPress, EquipmentStatuses.AVAILABLE, "admin",
                "last open report resolved");
    }

    @Test
    void anOutOfOrderMachineStaysOutWhileAnotherReportIsOpen() {
        legPress.setStatus(EquipmentStatuses.OUT_OF_ORDER);
        EquipmentIssueReport report = report(1L, legPress, IssueSeverity.DAMAGED, IssueStatus.ACKNOWLEDGED, NOW);
        when(issueReportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(issueReportRepository.save(report)).thenReturn(report);
        when(issueReportRepository.existsByEquipmentIdAndStatusNot(10L, IssueStatus.RESOLVED)).thenReturn(true);

        service.updateStatus(1L, IssueStatus.RESOLVED, "admin");

        verifyNoInteractions(machineStatusService);
    }

    @Test
    void resolvingReportsLeavesAMachineThatIsNotOutOfOrderAlone() {
        legPress.setStatus(EquipmentStatuses.IN_USE);
        EquipmentIssueReport report = report(1L, legPress, IssueSeverity.DAMAGED, IssueStatus.REPORTED, NOW);
        when(issueReportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(issueReportRepository.save(report)).thenReturn(report);

        service.updateStatus(1L, IssueStatus.RESOLVED, "admin");

        verifyNoInteractions(machineStatusService);
    }

    @Test
    void reopeningAResolvedReportDoesNotTakeTheMachineOutOfService() {
        EquipmentIssueReport report = report(1L, legPress, IssueSeverity.OUT_OF_ORDER, IssueStatus.RESOLVED, NOW);
        when(issueReportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(issueReportRepository.save(report)).thenReturn(report);

        service.updateStatus(1L, IssueStatus.IN_PROGRESS, "admin");

        verifyNoInteractions(machineStatusService);
        assertEquals(EquipmentStatuses.AVAILABLE, legPress.getStatus());
    }

    @Test
    void resolvingEveryOpenReportOnAMachinePutsItBackInService() {
        legPress.setStatus(EquipmentStatuses.OUT_OF_ORDER);
        EquipmentIssueReport first = report(1L, legPress, IssueSeverity.OUT_OF_ORDER, IssueStatus.IN_PROGRESS, NOW);
        EquipmentIssueReport second = report(2L, legPress, IssueSeverity.DAMAGED, IssueStatus.REPORTED, NOW.minusSeconds(60));
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(legPress));
        when(issueReportRepository.findByEquipmentIdAndStatusNotOrderByReportedAtDesc(10L, IssueStatus.RESOLVED))
                .thenReturn(List.of(first, second));
        when(issueReportRepository.existsByEquipmentIdAndStatusNot(10L, IssueStatus.RESOLVED)).thenReturn(false);
        when(machineStatusService.changeStatus(legPress, EquipmentStatuses.AVAILABLE, "admin", "last open report resolved"))
                .thenAnswer(inv -> {
                    legPress.setStatus(EquipmentStatuses.AVAILABLE);
                    return legPress;
                });

        EquipmentIssueGroupResponse group = service.updateStatusForEquipment(10L, IssueStatus.RESOLVED, "admin");

        assertEquals(EquipmentStatuses.AVAILABLE, group.getEquipmentStatus());
        assertEquals(0, group.getOpenReportCount());
    }

    @Test
    void setOutOfOrderTakesAMachineOutOfService() {
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(legPress));
        when(machineStatusService.changeStatus(legPress, EquipmentStatuses.OUT_OF_ORDER, "admin", null))
                .thenAnswer(inv -> {
                    legPress.setStatus(EquipmentStatuses.OUT_OF_ORDER);
                    return legPress;
                });

        AdminEquipmentResponse response = service.setOutOfOrder(10L, true, "admin");

        assertEquals(EquipmentStatuses.OUT_OF_ORDER, response.getStatus());
    }

    @Test
    void puttingBackInServiceNeverTurnsAnInUseMachineAvailable() {
        legPress.setStatus(EquipmentStatuses.IN_USE);
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(legPress));

        AdminEquipmentResponse response = service.setOutOfOrder(10L, false, "admin");

        assertEquals(EquipmentStatuses.IN_USE, response.getStatus());
        verifyNoInteractions(machineStatusService);
    }

    @Test
    void markingAnAlreadyOutOfOrderMachineChangesNothing() {
        legPress.setStatus(EquipmentStatuses.OUT_OF_ORDER);
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(legPress));

        service.setOutOfOrder(10L, true, "admin");

        verifyNoInteractions(machineStatusService);
    }

    @Test
    void setOutOfOrderOnARemovedMachineIsNotFound() {
        legPress.setDeleted(true);
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(legPress));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.setOutOfOrder(10L, true, "admin"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(machineStatusService);
    }

    private static Equipment machine(long id, String name) {
        Equipment equipment = new Equipment(null, name, "Available", null);
        equipment.setId(id);
        return equipment;
    }

    private EquipmentIssueReport report(long id, Equipment equipment, IssueSeverity severity,
                                        IssueStatus status, Instant reportedAt) {
        EquipmentIssueReport report = new EquipmentIssueReport(equipment, member, severity, "Something is wrong here");
        report.setId(id);
        report.setStatus(status);
        report.setReportedAt(reportedAt);
        report.setUpdatedAt(reportedAt);
        return report;
    }
}
