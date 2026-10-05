package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.CreateHelpRequestRequest;
import com.ureclive.urec_live_backend.dto.HelpDemoLink;
import com.ureclive.urec_live_backend.dto.HelpRequestResponse;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.entity.HelpRequest;
import com.ureclive.urec_live_backend.entity.HelpRequestClosedBy;
import com.ureclive.urec_live_backend.entity.HelpRequestStatus;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.ExerciseRepository;
import com.ureclive.urec_live_backend.repository.HelpRequestRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HelpRequestServiceTest {

    private static final String PLACEHOLDER_VIDEO = "https://cdn.example.com/placeholder.mp4";
    private static final String PLACEHOLDER_GIF = "https://cdn.example.com/placeholder.gif";

    @Mock private HelpRequestRepository helpRequestRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private UserRepository userRepository;
    @Mock private ActivityLogService activityLogService;
    @Spy private HelpDemoLinks helpDemoLinks = new HelpDemoLinks(PLACEHOLDER_VIDEO, PLACEHOLDER_GIF);

    @InjectMocks private HelpRequestService service;

    private User member;
    private User otherMember;
    private Equipment bench;
    private Exercise benchPress;

    @BeforeEach
    void setUp() {
        member = new User("jdoe", "jdoe@example.com", "pw");
        member.setId(1L);
        otherMember = new User("asmith", "asmith@example.com", "pw");
        otherMember.setId(2L);
        benchPress = new Exercise("Bench Press", "Chest", "https://via.placeholder.com/200x200?text=Bench+Press");
        benchPress.setId(30L);
        bench = new Equipment("BP001", "Flat Bench Press 1", "Available", null);
        bench.setId(10L);
        bench.addExercise(benchPress);
    }

    private static CreateHelpRequestRequest byId(Long equipmentId, String exerciseName) {
        CreateHelpRequestRequest request = new CreateHelpRequestRequest();
        request.setEquipmentId(equipmentId);
        request.setExerciseName(exerciseName);
        return request;
    }

    private static CreateHelpRequestRequest byCode(String code) {
        CreateHelpRequestRequest request = new CreateHelpRequestRequest();
        request.setEquipmentCode(code);
        return request;
    }

    private void memberExists() {
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(member));
    }

    private void saveReturnsArgument() {
        when(helpRequestRepository.save(any(HelpRequest.class))).thenAnswer(inv -> {
            HelpRequest saved = inv.getArgument(0);
            saved.setId(100L);
            return saved;
        });
    }

    private HelpRequest existing(User owner, HelpRequestStatus status) {
        HelpRequest request = new HelpRequest(owner, bench, null);
        request.setId(100L);
        request.setStatus(status);
        return request;
    }

    // ── Calling staff ───────────────────────────────────────────────────────

    @Test
    void create_savesARequestReceivedRequestAndLogsIt() {
        memberExists();
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(bench));
        saveReturnsArgument();

        HelpRequestResponse response = service.create(byId(10L, "bench press"), "jdoe");

        ArgumentCaptor<HelpRequest> saved = ArgumentCaptor.forClass(HelpRequest.class);
        verify(helpRequestRepository).save(saved.capture());
        assertSame(member, saved.getValue().getMember());
        assertSame(bench, saved.getValue().getEquipment());
        assertSame(benchPress, saved.getValue().getExercise());
        assertEquals(HelpRequestStatus.REQUEST_RECEIVED, response.getStatus());
        assertEquals(100L, response.getId());
        assertEquals("BP001", response.getEquipmentCode());
        assertEquals("Bench Press", response.getExerciseName());
        verify(activityLogService).log("HELP_REQUESTED", "jdoe", "Asked for help at Flat Bench Press 1",
                "Flat Bench Press 1");
    }

    @Test
    void create_returnsPlaceholderDemoMediaInsteadOfTheDeadSeededGif() {
        memberExists();
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(bench));
        saveReturnsArgument();

        HelpRequestResponse response = service.create(byId(10L, null), "jdoe");

        assertEquals(1, response.getDemos().size());
        HelpDemoLink demo = response.getDemos().get(0);
        assertEquals("Bench Press", demo.getExerciseName());
        assertEquals(PLACEHOLDER_GIF, demo.getGifUrl());
        assertTrue(demo.isGifPlaceholder());
        assertEquals(PLACEHOLDER_VIDEO, demo.getVideoUrl());
        assertTrue(demo.isVideoPlaceholder());
        verify(helpDemoLinks).forRequest(bench, null);
    }

    @Test
    void create_findsTheMachineByCodeWhenNoIdIsGiven() {
        memberExists();
        when(equipmentRepository.findByCode("BP001")).thenReturn(Optional.of(bench));
        saveReturnsArgument();

        HelpRequestResponse response = service.create(byCode(" BP001 "), "jdoe");

        assertEquals(10L, response.getEquipmentId());
        verify(equipmentRepository, never()).findById(any());
    }

    @Test
    void create_requiresAMachine() {
        memberExists();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(byCode("  "), "jdoe"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(helpRequestRepository, never()).save(any());
    }

    @Test
    void create_rejectsAnUnknownMachine() {
        memberExists();
        when(equipmentRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(byId(99L, null), "jdoe"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void create_rejectsARemovedMachine() {
        memberExists();
        bench.setDeleted(true);
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(bench));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(byId(10L, null), "jdoe"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(helpRequestRepository, never()).save(any());
    }

    @Test
    void create_rejectsASecondOpenRequest() {
        memberExists();
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(bench));
        when(helpRequestRepository.existsByMemberAndStatusIn(member, HelpRequestStatus.OPEN)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(byId(10L, null), "jdoe"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(helpRequestRepository, never()).save(any());
        verifyNoInteractions(activityLogService);
    }

    @Test
    void create_rejectsAnUnknownMember() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(byId(10L, null), "ghost"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ── Exercise matching ───────────────────────────────────────────────────

    @Test
    void resolveExercise_prefersTheMachinesOwnExerciseIgnoringCase() {
        assertSame(benchPress, service.resolveExercise(bench, "  BENCH PRESS "));
        verifyNoInteractions(exerciseRepository);
    }

    @Test
    void resolveExercise_fallsBackToAnyExerciseWithThatName() {
        Exercise incline = new Exercise("Incline Press", "Chest", null);
        when(exerciseRepository.findByNameIgnoreCase("Incline Press")).thenReturn(Optional.of(incline));

        assertSame(incline, service.resolveExercise(bench, "Incline Press"));
    }

    @Test
    void resolveExercise_ignoresTheMachineCodeAndBlankNames() {
        when(exerciseRepository.findByNameIgnoreCase("BP001")).thenReturn(Optional.empty());

        assertNull(service.resolveExercise(bench, "BP001"));
        assertNull(service.resolveExercise(bench, null));
        assertNull(service.resolveExercise(bench, "   "));
    }

    // ── Reading ─────────────────────────────────────────────────────────────

    @Test
    void getActive_returnsTheOpenRequest() {
        memberExists();
        when(helpRequestRepository.findFirstByMemberAndStatusInOrderByCreatedAtDesc(member, HelpRequestStatus.OPEN))
                .thenReturn(Optional.of(existing(member, HelpRequestStatus.ON_THE_WAY)));

        Optional<HelpRequestResponse> active = service.getActive("jdoe");

        assertTrue(active.isPresent());
        assertEquals(HelpRequestStatus.ON_THE_WAY, active.get().getStatus());
    }

    @Test
    void getActive_isEmptyWithoutAnOpenRequest() {
        memberExists();
        when(helpRequestRepository.findFirstByMemberAndStatusInOrderByCreatedAtDesc(member, HelpRequestStatus.OPEN))
                .thenReturn(Optional.empty());

        assertTrue(service.getActive("jdoe").isEmpty());
    }

    @Test
    void get_returnsTheMembersOwnClosedRequestToo() {
        memberExists();
        HelpRequest closed = existing(member, HelpRequestStatus.RESOLVED);
        closed.setClosedBy(HelpRequestClosedBy.STAFF);
        when(helpRequestRepository.findWithDetailsById(100L)).thenReturn(Optional.of(closed));

        HelpRequestResponse response = service.get(100L, "jdoe");

        assertEquals(HelpRequestStatus.RESOLVED, response.getStatus());
        assertEquals(HelpRequestClosedBy.STAFF, response.getClosedBy());
    }

    @Test
    void get_hidesOtherMembersRequestsAsNotFound() {
        memberExists();
        when(helpRequestRepository.findWithDetailsById(100L))
                .thenReturn(Optional.of(existing(otherMember, HelpRequestStatus.REQUEST_RECEIVED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.get(100L, "jdoe"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ── Member closes it ────────────────────────────────────────────────────

    @Test
    void confirmReceived_resolvesTheRequestAsClosedByTheMember() {
        memberExists();
        when(helpRequestRepository.findWithDetailsById(100L))
                .thenReturn(Optional.of(existing(member, HelpRequestStatus.ON_THE_WAY)));
        when(helpRequestRepository.saveAndFlush(any(HelpRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        HelpRequestResponse response = service.confirmReceived(100L, "jdoe");

        assertEquals(HelpRequestStatus.RESOLVED, response.getStatus());
        assertEquals(HelpRequestClosedBy.MEMBER, response.getClosedBy());
        assertNotNull(response.getClosedAt());
        verify(activityLogService).log("HELP_CLOSED", "jdoe", "Confirmed they received help at Flat Bench Press 1",
                "Flat Bench Press 1");
    }

    @Test
    void cancel_cancelsTheRequest() {
        memberExists();
        when(helpRequestRepository.findWithDetailsById(100L))
                .thenReturn(Optional.of(existing(member, HelpRequestStatus.TOO_BUSY)));
        when(helpRequestRepository.saveAndFlush(any(HelpRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        HelpRequestResponse response = service.cancel(100L, "jdoe");

        assertEquals(HelpRequestStatus.CANCELLED, response.getStatus());
        assertEquals(HelpRequestClosedBy.MEMBER, response.getClosedBy());
        verify(activityLogService).log("HELP_CLOSED", "jdoe", "Cancelled their help request at Flat Bench Press 1",
                "Flat Bench Press 1");
    }

    @Test
    void confirmReceived_rejectsAnAlreadyClosedRequest() {
        memberExists();
        when(helpRequestRepository.findWithDetailsById(100L))
                .thenReturn(Optional.of(existing(member, HelpRequestStatus.RESOLVED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirmReceived(100L, "jdoe"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(helpRequestRepository, never()).saveAndFlush(any());
        verifyNoInteractions(activityLogService);
    }

    @Test
    void cancel_cannotCloseSomeoneElsesRequest() {
        memberExists();
        when(helpRequestRepository.findWithDetailsById(100L))
                .thenReturn(Optional.of(existing(otherMember, HelpRequestStatus.REQUEST_RECEIVED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.cancel(100L, "jdoe"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(helpRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmReceived_turnsAConcurrentStaffUpdateInto409() {
        memberExists();
        when(helpRequestRepository.findWithDetailsById(100L))
                .thenReturn(Optional.of(existing(member, HelpRequestStatus.REQUEST_RECEIVED)));
        when(helpRequestRepository.saveAndFlush(any(HelpRequest.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(HelpRequest.class, 100L));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirmReceived(100L, "jdoe"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verifyNoInteractions(activityLogService);
    }
}
