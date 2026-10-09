package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.HelpRequest;
import com.ureclive.urec_live_backend.entity.HelpRequestClosedBy;
import com.ureclive.urec_live_backend.entity.HelpRequestStatus;
import com.ureclive.urec_live_backend.service.AdminHelpRequestService;
import com.ureclive.urec_live_backend.service.HelpDemoLinks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests of the help request API: a member calls staff, staff respond and finish, and
 * the member sees each step. Runs the real security chain, validation and JPA on H2.
 */
class HelpRequestApiIntegrationTest extends HelpRequestApiTestSupport {

    @Autowired private AdminHelpRequestService adminHelpRequestService;

    private String member;
    private String otherMember;
    private String admin;
    private String roleAdmin;
    private Equipment bench;
    private Equipment rower;

    @BeforeEach
    void setUp() {
        helpRequestRepository.deleteAll();
        member = tokenFor("hr-member", "USER");
        otherMember = tokenFor("hr-other", "USER");
        admin = tokenFor("hr-admin", "ADMIN");
        roleAdmin = tokenFor("hr-role-admin", "ROLE_ADMIN");
        bench = machine("HR-BP", "Test Bench 1", false, "HR Test Press");
        rower = machine("HR-ROW", "Test Rower 2", false, null);
    }

    // ── Security ────────────────────────────────────────────────────────────

    @Test
    void everyEndpointNeedsSigningIn() throws Exception {
        postJsonAs(null, MEMBER_API, fields("equipmentId", bench.getId())).andExpect(status().isUnauthorized());
        getAs(null, MEMBER_API + "/me/active").andExpect(status().isUnauthorized());
        getAs(null, MEMBER_API + "/1").andExpect(status().isUnauthorized());
        postAs(null, MEMBER_API + "/1/received").andExpect(status().isUnauthorized());
        postAs(null, MEMBER_API + "/1/cancel").andExpect(status().isUnauthorized());
        getAs(null, ADMIN_API).andExpect(status().isUnauthorized());
        getAs(null, ADMIN_API + "/history").andExpect(status().isUnauthorized());
        putStatusAs(null, 1, "ON_THE_WAY").andExpect(status().isUnauthorized());
        postAs(null, ADMIN_API + "/1/done").andExpect(status().isUnauthorized());
    }

    @Test
    void membersCannotUseTheStaffEndpoints() throws Exception {
        long id = callStaff(member, bench);

        getAs(member, ADMIN_API).andExpect(status().isForbidden());
        getAs(member, ADMIN_API + "/history").andExpect(status().isForbidden());
        putStatusAs(member, id, "ON_THE_WAY").andExpect(status().isForbidden());
        postAs(member, ADMIN_API + "/" + id + "/done").andExpect(status().isForbidden());

        getAs(member, MEMBER_API + "/" + id).andExpect(jsonPath("$.status").value("REQUEST_RECEIVED"));
    }

    @Test
    void bothAdminRoleNamesCanWorkTheQueue() throws Exception {
        long id = callStaff(member, bench);

        getAs(admin, ADMIN_API).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        getAs(roleAdmin, ADMIN_API).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        putStatusAs(admin, id, "TOO_BUSY").andExpect(status().isOk());
        putStatusAs(roleAdmin, id, "ON_THE_WAY").andExpect(status().isOk())
                .andExpect(jsonPath("$.staffUsername").value("hr-role-admin"));
    }

    @Test
    void membersCannotSeeOrCloseSomeoneElsesRequest() throws Exception {
        long id = callStaff(member, bench);

        getAs(otherMember, MEMBER_API + "/" + id).andExpect(status().isNotFound());
        postAs(otherMember, MEMBER_API + "/" + id + "/received").andExpect(status().isNotFound());
        postAs(otherMember, MEMBER_API + "/" + id + "/cancel").andExpect(status().isNotFound());
        getAs(otherMember, MEMBER_API + "/me/active").andExpect(status().isNoContent());

        getAs(member, MEMBER_API + "/" + id).andExpect(jsonPath("$.status").value("REQUEST_RECEIVED"));
    }

    // ── Calling staff ───────────────────────────────────────────────────────

    @Test
    void memberCallsStaffAndGetsPlaceholderDemoMedia() throws Exception {
        postJsonAs(member, MEMBER_API, fields("equipmentId", bench.getId(), "exerciseName", "hr test press"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("REQUEST_RECEIVED"))
                .andExpect(jsonPath("$.equipmentId").value(bench.getId()))
                .andExpect(jsonPath("$.equipmentCode").value("HR-BP"))
                .andExpect(jsonPath("$.equipmentName").value("Test Bench 1"))
                .andExpect(jsonPath("$.exerciseName").value("HR Test Press"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.closedAt").value(nullValue()))
                .andExpect(jsonPath("$.demos", hasSize(1)))
                .andExpect(jsonPath("$.demos[0].exerciseName").value("HR Test Press"))
                .andExpect(jsonPath("$.demos[0].gifUrl").value(HelpDemoLinks.DEFAULT_PLACEHOLDER_GIF_URL))
                .andExpect(jsonPath("$.demos[0].gifPlaceholder").value(true))
                .andExpect(jsonPath("$.demos[0].videoUrl").value(HelpDemoLinks.DEFAULT_PLACEHOLDER_VIDEO_URL))
                .andExpect(jsonPath("$.demos[0].videoPlaceholder").value(true));
    }

    @Test
    void seededMachinesShowThePlaceholderGifInsteadOfTheirDeadOnes() throws Exception {
        Equipment seeded = equipmentRepository.findByCode("BP001").orElseThrow();

        postJsonAs(member, MEMBER_API, fields("equipmentId", seeded.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.demos[0].exerciseName").value("Bench Press"))
                .andExpect(jsonPath("$.demos[*].gifUrl", everyItem(is(HelpDemoLinks.DEFAULT_PLACEHOLDER_GIF_URL))))
                .andExpect(jsonPath("$.demos[*].gifPlaceholder", everyItem(is(true))))
                .andExpect(jsonPath("$.demos[*].videoUrl", everyItem(is(HelpDemoLinks.DEFAULT_PLACEHOLDER_VIDEO_URL))));
    }

    @Test
    void anAdminSetGifWinsOverThePlaceholderGif() throws Exception {
        // Its own machine and exercise: test data outlives each test, and the others expect placeholder GIFs
        Equipment gifPress = machine("HR-GIF", "Test Gif Press 1", false, "HR Gif Press");
        String realGif = "https://media.example.com/hr-gif-press.gif";
        transactionTemplate.executeWithoutResult(tx -> exerciseRepository.findByNameIgnoreCase("HR Gif Press")
                .orElseThrow()
                .setGifUrl(realGif));

        postJsonAs(member, MEMBER_API, fields("equipmentId", gifPress.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.demos[0].gifUrl").value(realGif))
                .andExpect(jsonPath("$.demos[0].gifPlaceholder").value(false))
                .andExpect(jsonPath("$.demos[0].videoUrl").value(HelpDemoLinks.DEFAULT_PLACEHOLDER_VIDEO_URL))
                .andExpect(jsonPath("$.demos[0].videoPlaceholder").value(true));
    }

    @Test
    void aMachineWithNoExercisesGetsAPlaceholderDemoForTheMachine() throws Exception {
        postJsonAs(member, MEMBER_API, fields("equipmentCode", "HR-ROW"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.equipmentId").value(rower.getId()))
                .andExpect(jsonPath("$.exerciseName").value(nullValue()))
                .andExpect(jsonPath("$.demos", hasSize(1)))
                .andExpect(jsonPath("$.demos[0].exerciseName").value("Test Rower"))
                .andExpect(jsonPath("$.demos[0].videoUrl").value(HelpDemoLinks.DEFAULT_PLACEHOLDER_VIDEO_URL))
                .andExpect(jsonPath("$.demos[0].gifUrl").value(HelpDemoLinks.DEFAULT_PLACEHOLDER_GIF_URL));
    }

    @Test
    void theTrackersMachineCodeAsExerciseIsIgnored() throws Exception {
        postJsonAs(member, MEMBER_API, fields("equipmentCode", "HR-BP", "exerciseName", "HR-BP"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exerciseName").value(nullValue()))
                .andExpect(jsonPath("$.demos[0].exerciseName").value("HR Test Press"));
    }

    @Test
    void unknownAndRemovedMachinesAre404() throws Exception {
        Equipment removed = machine("HR-GONE", "Removed Machine", true, null);

        postJsonAs(member, MEMBER_API, fields("equipmentId", 999_999)).andExpect(status().isNotFound());
        postJsonAs(member, MEMBER_API, fields("equipmentCode", "NOPE-404")).andExpect(status().isNotFound());
        postJsonAs(member, MEMBER_API, fields("equipmentId", removed.getId())).andExpect(status().isNotFound());
        assertEquals(0, helpRequestRepository.count());
    }

    static Stream<String> invalidCreateBodies() {
        return Stream.of(
                "{}",
                "{\"equipmentCode\":\"   \"}",
                "{\"equipmentId\":\"abc\"}",
                "{\"equipmentCode\":\"" + "X".repeat(51) + "\"}",
                "{\"equipmentId\":1,\"exerciseName\":\"" + "Y".repeat(101) + "\"}",
                "not json");
    }

    @ParameterizedTest(name = "{0} -> 400")
    @MethodSource("invalidCreateBodies")
    void invalidCreateRequestsAre400(String body) throws Exception {
        mvc.perform(post(MEMBER_API).header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, helpRequestRepository.count());
    }

    @Test
    void aMemberCanOnlyHaveOneOpenRequest() throws Exception {
        long id = callStaff(member, bench);

        postJsonAs(member, MEMBER_API, fields("equipmentId", bench.getId())).andExpect(status().isConflict());
        postJsonAs(member, MEMBER_API, fields("equipmentId", rower.getId())).andExpect(status().isConflict());
        // Another member can still call staff to the same machine
        callStaff(otherMember, bench);

        postAs(member, MEMBER_API + "/" + id + "/cancel").andExpect(status().isOk());
        callStaff(member, rower);
        assertEquals(3, helpRequestRepository.count());
    }

    @Test
    void activeRequestIs204UntilTheMemberCallsStaff() throws Exception {
        getAs(member, MEMBER_API + "/me/active").andExpect(status().isNoContent());

        long id = callStaff(member, bench);
        getAs(member, MEMBER_API + "/me/active").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.demos", not(empty())));

        postAs(member, MEMBER_API + "/" + id + "/received").andExpect(status().isOk());
        getAs(member, MEMBER_API + "/me/active").andExpect(status().isNoContent());
    }

    // ── Staff work the request ──────────────────────────────────────────────

    @Test
    void fullFlowFromCallToDoneHelping() throws Exception {
        long id = body(postJsonAs(member, MEMBER_API,
                fields("equipmentId", bench.getId(), "exerciseName", "HR Test Press"))
                .andExpect(status().isCreated())).get("id").asLong();

        getAs(admin, ADMIN_API).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].status").value("REQUEST_RECEIVED"))
                .andExpect(jsonPath("$[0].memberUsername").value("hr-member"))
                .andExpect(jsonPath("$[0].equipmentCode").value("HR-BP"))
                .andExpect(jsonPath("$[0].exerciseName").value("HR Test Press"))
                .andExpect(jsonPath("$[0].staffUsername").value(nullValue()));

        putStatusAs(admin, id, "TOO_BUSY").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TOO_BUSY"))
                .andExpect(jsonPath("$.firstResponseAt").isNotEmpty());
        getAs(member, MEMBER_API + "/" + id).andExpect(jsonPath("$.status").value("TOO_BUSY"));

        putStatusAs(admin, id, "ON_THE_WAY").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_THE_WAY"))
                .andExpect(jsonPath("$.staffUsername").value("hr-admin"));
        getAs(member, MEMBER_API + "/" + id).andExpect(jsonPath("$.status").value("ON_THE_WAY"))
                // Members never learn which staff account is coming
                .andExpect(jsonPath("$.staffUsername").doesNotExist())
                .andExpect(jsonPath("$.memberUsername").doesNotExist());

        postAs(admin, ADMIN_API + "/" + id + "/done").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.closedBy").value("STAFF"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        getAs(member, MEMBER_API + "/" + id).andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.closedBy").value("STAFF"));
        getAs(member, MEMBER_API + "/me/active").andExpect(status().isNoContent());
        getAs(admin, ADMIN_API).andExpect(jsonPath("$", hasSize(0)));
        getAs(admin, ADMIN_API + "/history").andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].closedBy").value("STAFF"));
    }

    @Test
    void memberConfirmsTheyReceivedHelp() throws Exception {
        long id = callStaff(member, bench);
        putStatusAs(admin, id, "ON_THE_WAY").andExpect(status().isOk());

        postAs(member, MEMBER_API + "/" + id + "/received").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.closedBy").value("MEMBER"));

        getAs(admin, ADMIN_API).andExpect(jsonPath("$", hasSize(0)));
        getAs(admin, ADMIN_API + "/history").andExpect(jsonPath("$[0].closedBy").value("MEMBER"))
                .andExpect(jsonPath("$[0].staffUsername").value("hr-admin"));
    }

    @Test
    void aClosedRequestCannotBeReopenedOrClosedAgain() throws Exception {
        long id = callStaff(member, bench);
        postAs(member, MEMBER_API + "/" + id + "/received").andExpect(status().isOk());

        putStatusAs(admin, id, "ON_THE_WAY").andExpect(status().isConflict());
        postAs(admin, ADMIN_API + "/" + id + "/done").andExpect(status().isConflict());
        postAs(member, MEMBER_API + "/" + id + "/received").andExpect(status().isConflict());
        postAs(member, MEMBER_API + "/" + id + "/cancel").andExpect(status().isConflict());

        getAs(member, MEMBER_API + "/" + id).andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.closedBy").value("MEMBER"));
    }

    @Test
    void memberCancelsTheRequest() throws Exception {
        long id = callStaff(member, bench);

        postAs(member, MEMBER_API + "/" + id + "/cancel").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.closedBy").value("MEMBER"));

        getAs(admin, ADMIN_API).andExpect(jsonPath("$", hasSize(0)));
        getAs(admin, ADMIN_API + "/history").andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    @ParameterizedTest(name = "staff can't set {0}")
    @ValueSource(strings = {"REQUEST_RECEIVED", "RESOLVED", "CANCELLED", "EXPIRED", "ASAP", ""})
    void staffCanOnlySetOnTheWayOrTooBusy(String status) throws Exception {
        long id = callStaff(member, bench);

        putStatusAs(admin, id, status).andExpect(status().isBadRequest());
        getAs(member, MEMBER_API + "/" + id).andExpect(jsonPath("$.status").value("REQUEST_RECEIVED"));
    }

    @Test
    void aStatusBodyIsRequired() throws Exception {
        long id = callStaff(member, bench);

        mvc.perform(put(ADMIN_API + "/" + id + "/status").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffActionsOnUnknownRequestsAre404() throws Exception {
        putStatusAs(admin, 999_999, "ON_THE_WAY").andExpect(status().isNotFound());
        postAs(admin, ADMIN_API + "/999999/done").andExpect(status().isNotFound());
    }

    @Test
    void queueIsOldestFirstAndHistoryNewestFirst() throws Exception {
        long first = callStaff(member, bench);
        long second = callStaff(otherMember, rower);

        getAs(admin, ADMIN_API).andExpect(jsonPath("$[*].id", contains((int) first, (int) second)));

        postAs(admin, ADMIN_API + "/" + first + "/done").andExpect(status().isOk());
        postAs(admin, ADMIN_API + "/" + second + "/done").andExpect(status().isOk());
        getAs(admin, ADMIN_API + "/history?limit=1").andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(second));
    }

    // ── Expiry ──────────────────────────────────────────────────────────────

    /** Makes the request look untouched since {@code ago}, as if the member walked away. */
    private void age(long id, Duration ago) {
        transactionTemplate.executeWithoutResult(tx -> {
            HelpRequest request = helpRequestRepository.findById(id).orElseThrow();
            request.setUpdatedAt(Instant.now().minus(ago));
            helpRequestRepository.save(request);
        });
    }

    @Test
    void idleRequestsExpireAndTheMemberCanAskAgain() throws Exception {
        long idle = callStaff(member, bench);
        long fresh = callStaff(otherMember, bench);
        age(idle, Duration.ofMinutes(31));

        // Asserts the final state rather than the returned count: the real expiry job also runs
        // every minute in this shared context and may get there first.
        adminHelpRequestService.expireIdleBefore(Instant.now().minus(Duration.ofMinutes(30)));

        HelpRequest expired = helpRequestRepository.findById(idle).orElseThrow();
        assertEquals(HelpRequestStatus.EXPIRED, expired.getStatus());
        assertEquals(HelpRequestClosedBy.SYSTEM, expired.getClosedBy());
        assertNotNull(expired.getClosedAt());
        assertEquals(HelpRequestStatus.REQUEST_RECEIVED, helpRequestRepository.findById(fresh).orElseThrow().getStatus());

        getAs(member, MEMBER_API + "/" + idle).andExpect(jsonPath("$.status").value("EXPIRED"));
        getAs(member, MEMBER_API + "/me/active").andExpect(status().isNoContent());
        callStaff(member, rower);
    }

    @Test
    void aStaffResponseKeepsARequestFromExpiring() throws Exception {
        long id = callStaff(member, bench);
        age(id, Duration.ofMinutes(45));
        putStatusAs(admin, id, "ON_THE_WAY").andExpect(status().isOk());

        adminHelpRequestService.expireIdleBefore(Instant.now().minus(Duration.ofMinutes(30)));

        assertEquals(HelpRequestStatus.ON_THE_WAY, helpRequestRepository.findById(id).orElseThrow().getStatus());
    }
}
