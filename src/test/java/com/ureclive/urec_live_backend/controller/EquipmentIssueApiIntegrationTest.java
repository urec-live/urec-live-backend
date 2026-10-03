package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.IssueStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for equipment issue reporting: real controllers, security (JWT filter and
 * {@code @PreAuthorize}), validation, services and JPA queries, against an in-memory H2 database.
 */
class EquipmentIssueApiIntegrationTest extends ApiIntegrationTestSupport {

    private Equipment legPress;
    private Equipment bench;
    private Equipment removedRower;

    private String memberToken;
    private String otherMemberToken;
    private String adminToken;          // role "ADMIN", which the admin dashboard assigns
    private String prefixedAdminToken;  // role "ROLE_ADMIN"

    @BeforeEach
    void setUp() {
        issueReportRepository.deleteAll();

        legPress = machine("IT-LP", "IT Leg Press", false);
        bench = machine("IT-BP", "IT Bench Press", false);
        removedRower = machine("IT-RW", "IT Rower", true);

        memberToken = tokenFor("it_member", "USER");
        otherMemberToken = tokenFor("it_member2", "USER");
        adminToken = tokenFor("it_admin", "ADMIN");
        prefixedAdminToken = tokenFor("it_admin_prefixed", "ROLE_ADMIN");
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Test
    void memberEndpointsRequireAuthentication() throws Exception {
        mvc.perform(json(post(MEMBER_API), validReport(legPress))).andExpect(status().isUnauthorized());
        mvc.perform(get(MEMBER_API + "/me")).andExpect(status().isUnauthorized());
        mvc.perform(get(MEMBER_API + "/equipment/" + legPress.getId())).andExpect(status().isUnauthorized());
        assertEquals(0L, issueReportRepository.count());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        getAs("not-a-real-token", MEMBER_API + "/me").andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsRejectMembers() throws Exception {
        long reportId = fileReport(memberToken, legPress, "DAMAGED");

        getAs(memberToken, ADMIN_API).andExpect(status().isForbidden());
        getAs(memberToken, ADMIN_API + "/summary").andExpect(status().isForbidden());
        putStatusAs(memberToken, ADMIN_API + "/" + reportId + "/status", "RESOLVED")
                .andExpect(status().isForbidden());
        putStatusAs(memberToken, ADMIN_API + "/equipment/" + legPress.getId() + "/status", "RESOLVED")
                .andExpect(status().isForbidden());

        assertEquals(IssueStatus.REPORTED, issueReportRepository.findById(reportId).orElseThrow().getStatus());
    }

    @Test
    void adminEndpointsAcceptBothAdminRoleNames() throws Exception {
        getAs(adminToken, ADMIN_API).andExpect(status().isOk());
        getAs(prefixedAdminToken, ADMIN_API).andExpect(status().isOk());
        getAs(prefixedAdminToken, ADMIN_API + "/summary").andExpect(status().isOk());
    }

    // ── Reporting a problem ───────────────────────────────────────────────────

    @Test
    void memberCanReportAProblem() throws Exception {
        mvc.perform(json(post(MEMBER_API).header(HttpHeaders.AUTHORIZATION, bearer(memberToken)),
                        fields("equipmentId", legPress.getId(),
                               "severity", "OUT_OF_ORDER",
                               "description", "  Cable snapped, the weight stack won't lift  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.equipmentId").value(legPress.getId().intValue()))
                .andExpect(jsonPath("$.equipmentName").value("IT Leg Press"))
                .andExpect(jsonPath("$.equipmentCode").value("IT-LP"))
                .andExpect(jsonPath("$.severity").value("OUT_OF_ORDER"))
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.description").value("Cable snapped, the weight stack won't lift"))
                .andExpect(jsonPath("$.reporterUsername").value("it_member"))
                .andExpect(jsonPath("$.reportedAt").isNotEmpty())
                .andExpect(jsonPath("$.resolvedAt").value(nullValue()));

        assertEquals(1L, issueReportRepository.count());
    }

    @Test
    void secondOpenReportOnTheSameMachineIsRejected() throws Exception {
        fileReport(memberToken, legPress, "DAMAGED");

        mvc.perform(json(post(MEMBER_API).header(HttpHeaders.AUTHORIZATION, bearer(memberToken)),
                        validReport(legPress)))
                .andExpect(status().isConflict());

        // Other machines, and other members on the same machine, are unaffected
        fileReport(memberToken, bench, "DAMAGED");
        fileReport(otherMemberToken, legPress, "OUT_OF_ORDER");
        assertEquals(3L, issueReportRepository.count());
    }

    @Test
    void memberCanReportAgainOnceTheirReportIsResolved() throws Exception {
        long reportId = fileReport(memberToken, legPress, "DAMAGED");
        putStatusAs(adminToken, ADMIN_API + "/" + reportId + "/status", "RESOLVED").andExpect(status().isOk());

        fileReport(memberToken, legPress, "OUT_OF_ORDER");
        assertEquals(2L, issueReportRepository.count());
    }

    static Stream<Arguments> invalidReports() {
        return Stream.of(
                Arguments.of("missing equipmentId",
                        "{\"severity\":\"DAMAGED\",\"description\":\"Seat padding is torn\"}"),
                Arguments.of("missing severity",
                        "{\"equipmentId\":%d,\"description\":\"Seat padding is torn\"}"),
                Arguments.of("unknown severity",
                        "{\"equipmentId\":%d,\"severity\":\"BROKEN\",\"description\":\"Seat padding is torn\"}"),
                Arguments.of("missing description",
                        "{\"equipmentId\":%d,\"severity\":\"DAMAGED\"}"),
                Arguments.of("blank description",
                        "{\"equipmentId\":%d,\"severity\":\"DAMAGED\",\"description\":\"            \"}"),
                Arguments.of("description under 10 characters",
                        "{\"equipmentId\":%d,\"severity\":\"DAMAGED\",\"description\":\"broken\"}"),
                Arguments.of("description under 10 characters once trimmed",
                        "{\"equipmentId\":%d,\"severity\":\"DAMAGED\",\"description\":\"    broken    \"}"),
                Arguments.of("description over 1000 characters",
                        "{\"equipmentId\":%d,\"severity\":\"DAMAGED\",\"description\":\"" + "x".repeat(1001) + "\"}"),
                Arguments.of("malformed JSON", "{\"equipmentId\":%d,"));
    }

    @ParameterizedTest(name = "{0} -> 400")
    @MethodSource("invalidReports")
    void invalidReportsAreRejected(String caseName, String bodyTemplate) throws Exception {
        mvc.perform(post(MEMBER_API)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(bodyTemplate, legPress.getId())))
                .andExpect(status().isBadRequest());

        assertEquals(0L, issueReportRepository.count());
    }

    @Test
    void reportingAnUnknownOrRemovedMachineIsNotFound() throws Exception {
        mvc.perform(json(post(MEMBER_API).header(HttpHeaders.AUTHORIZATION, bearer(memberToken)),
                        fields("equipmentId", 999_999, "severity", "DAMAGED", "description", "Seat padding is torn")))
                .andExpect(status().isNotFound());
        mvc.perform(json(post(MEMBER_API).header(HttpHeaders.AUTHORIZATION, bearer(memberToken)),
                        validReport(removedRower)))
                .andExpect(status().isNotFound());

        assertEquals(0L, issueReportRepository.count());
    }

    @Test
    void myReportsListsOnlyTheCallersReportsNewestFirst() throws Exception {
        fileReport(memberToken, legPress, "DAMAGED");
        fileReport(otherMemberToken, legPress, "OUT_OF_ORDER");
        fileReport(memberToken, bench, "OUT_OF_ORDER");

        getAs(memberToken, MEMBER_API + "/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].equipmentName").value("IT Bench Press"))
                .andExpect(jsonPath("$[1].equipmentName").value("IT Leg Press"))
                .andExpect(jsonPath("$[*].reporterUsername", everyItem(is("it_member"))));

        getAs(adminToken, MEMBER_API + "/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── Machine page banner ───────────────────────────────────────────────────

    @Test
    void machineIssueStatusSummarisesOpenReportsWithoutReporterDetails() throws Exception {
        fileReport(memberToken, legPress, "DAMAGED");
        long notWorking = fileReport(otherMemberToken, legPress, "OUT_OF_ORDER");
        putStatusAs(adminToken, ADMIN_API + "/" + notWorking + "/status", "IN_PROGRESS").andExpect(status().isOk());

        getAs(memberToken, MEMBER_API + "/equipment/" + legPress.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentId").value(legPress.getId().intValue()))
                .andExpect(jsonPath("$.openReportCount").value(2))
                .andExpect(jsonPath("$.worstSeverity").value("OUT_OF_ORDER"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                // Every member can see this, so it must not leak who reported what
                .andExpect(jsonPath("$.reporterUsername").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.reports").doesNotExist());
    }

    @Test
    void machineIssueStatusIsEmptyWhenNothingIsOpen() throws Exception {
        long reportId = fileReport(memberToken, legPress, "OUT_OF_ORDER");
        putStatusAs(adminToken, ADMIN_API + "/" + reportId + "/status", "RESOLVED").andExpect(status().isOk());

        for (Equipment equipment : new Equipment[] { legPress, bench }) {
            getAs(memberToken, MEMBER_API + "/equipment/" + equipment.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.openReportCount").value(0))
                    .andExpect(jsonPath("$.worstSeverity").value(nullValue()))
                    .andExpect(jsonPath("$.status").value(nullValue()));
        }
    }

    // ── Admin issues view ─────────────────────────────────────────────────────

    @Test
    void adminSeesMachinesGroupedWithNotWorkingFirst() throws Exception {
        // The not-working report is the oldest, but its machine still comes first
        fileReport(memberToken, legPress, "OUT_OF_ORDER");
        fileReport(memberToken, bench, "DAMAGED");
        fileReport(otherMemberToken, bench, "DAMAGED");

        getAs(adminToken, ADMIN_API)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].equipmentName").value("IT Leg Press"))
                .andExpect(jsonPath("$[0].worstSeverity").value("OUT_OF_ORDER"))
                .andExpect(jsonPath("$[0].openReportCount").value(1))
                .andExpect(jsonPath("$[0].equipmentStatus").value("Available"))
                .andExpect(jsonPath("$[1].equipmentName").value("IT Bench Press"))
                .andExpect(jsonPath("$[1].worstSeverity").value("DAMAGED"))
                .andExpect(jsonPath("$[1].openReportCount").value(2))
                .andExpect(jsonPath("$[1].reports", hasSize(2)))
                // Reports within a machine are newest first, with full details for staff
                .andExpect(jsonPath("$[1].reports[0].reporterUsername").value("it_member2"))
                .andExpect(jsonPath("$[1].reports[1].reporterUsername").value("it_member"))
                .andExpect(jsonPath("$[1].reports[0].description").value("Something is clearly wrong with it"));
    }

    @Test
    void adminStatusChangesReachTheReporter() throws Exception {
        long reportId = fileReport(memberToken, legPress, "OUT_OF_ORDER");
        String url = ADMIN_API + "/" + reportId + "/status";

        putStatusAs(adminToken, url, "ACKNOWLEDGED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.resolvedAt").value(nullValue()));
        getAs(memberToken, MEMBER_API + "/me").andExpect(jsonPath("$[0].status").value("ACKNOWLEDGED"));

        putStatusAs(adminToken, url, "IN_PROGRESS").andExpect(status().isOk());
        getAs(memberToken, MEMBER_API + "/me").andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));

        putStatusAs(adminToken, url, "RESOLVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        getAs(memberToken, MEMBER_API + "/me")
                .andExpect(jsonPath("$[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$[0].resolvedAt").isNotEmpty());

        // Reopening (e.g. it broke again) clears resolvedAt
        putStatusAs(adminToken, url, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").value(nullValue()));
    }

    @Test
    void resolvedReportsAreHiddenUnlessRequested() throws Exception {
        long reportId = fileReport(memberToken, legPress, "DAMAGED");
        putStatusAs(adminToken, ADMIN_API + "/" + reportId + "/status", "RESOLVED").andExpect(status().isOk());

        getAs(adminToken, ADMIN_API).andExpect(jsonPath("$", hasSize(0)));
        getAs(adminToken, ADMIN_API + "?includeResolved=true")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].openReportCount").value(0))
                .andExpect(jsonPath("$[0].worstSeverity").value(nullValue()))
                .andExpect(jsonPath("$[0].reports[0].status").value("RESOLVED"));
    }

    @Test
    void settingStatusForAMachineUpdatesAllItsOpenReports() throws Exception {
        long alreadyResolved = fileReport(memberToken, legPress, "DAMAGED");
        putStatusAs(adminToken, ADMIN_API + "/" + alreadyResolved + "/status", "RESOLVED").andExpect(status().isOk());
        long first = fileReport(memberToken, legPress, "OUT_OF_ORDER");
        long second = fileReport(otherMemberToken, legPress, "DAMAGED");

        putStatusAs(adminToken, ADMIN_API + "/equipment/" + legPress.getId() + "/status", "ACKNOWLEDGED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentId").value(legPress.getId().intValue()))
                .andExpect(jsonPath("$.openReportCount").value(2))
                .andExpect(jsonPath("$.reports", hasSize(2)))
                .andExpect(jsonPath("$.reports[*].status", everyItem(is("ACKNOWLEDGED"))));

        assertEquals(IssueStatus.ACKNOWLEDGED, issueReportRepository.findById(first).orElseThrow().getStatus());
        assertEquals(IssueStatus.ACKNOWLEDGED, issueReportRepository.findById(second).orElseThrow().getStatus());
        assertEquals(IssueStatus.RESOLVED, issueReportRepository.findById(alreadyResolved).orElseThrow().getStatus());
    }

    @Test
    void settingStatusForAMachineWithNoOpenReportsIsNotFound() throws Exception {
        putStatusAs(adminToken, ADMIN_API + "/equipment/" + bench.getId() + "/status", "RESOLVED")
                .andExpect(status().isNotFound());
        putStatusAs(adminToken, ADMIN_API + "/equipment/999999/status", "RESOLVED")
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidStatusUpdatesAreRejected() throws Exception {
        long reportId = fileReport(memberToken, legPress, "DAMAGED");
        String url = ADMIN_API + "/" + reportId + "/status";

        putStatusAs(adminToken, url, "FIXED").andExpect(status().isBadRequest());
        mvc.perform(put(url)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        putStatusAs(adminToken, ADMIN_API + "/999999/status", "RESOLVED").andExpect(status().isNotFound());

        assertEquals(IssueStatus.REPORTED, issueReportRepository.findById(reportId).orElseThrow().getStatus());
    }

    @Test
    void summaryCountsReportsByStatus() throws Exception {
        long acknowledged = fileReport(memberToken, legPress, "OUT_OF_ORDER");
        long inProgress = fileReport(otherMemberToken, legPress, "DAMAGED");
        long resolved = fileReport(memberToken, bench, "DAMAGED");
        fileReport(otherMemberToken, bench, "DAMAGED"); // stays REPORTED
        putStatusAs(adminToken, ADMIN_API + "/" + acknowledged + "/status", "ACKNOWLEDGED").andExpect(status().isOk());
        putStatusAs(adminToken, ADMIN_API + "/" + inProgress + "/status", "IN_PROGRESS").andExpect(status().isOk());
        putStatusAs(adminToken, ADMIN_API + "/" + resolved + "/status", "RESOLVED").andExpect(status().isOk());

        getAs(adminToken, ADMIN_API + "/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reported").value(1))
                .andExpect(jsonPath("$.acknowledged").value(1))
                .andExpect(jsonPath("$.inProgress").value(1))
                .andExpect(jsonPath("$.affectedMachines").value(2))
                .andExpect(jsonPath("$.outOfOrderMachines").isNumber());
    }

    @Test
    void reportsOnRemovedMachinesDropOutOfTheAdminView() throws Exception {
        fileReport(memberToken, legPress, "OUT_OF_ORDER");
        legPress.setDeleted(true);
        equipmentRepository.save(legPress);

        getAs(adminToken, ADMIN_API).andExpect(jsonPath("$", hasSize(0)));
        getAs(adminToken, ADMIN_API + "?includeResolved=true").andExpect(jsonPath("$", hasSize(0)));
        getAs(adminToken, ADMIN_API + "/summary")
                .andExpect(jsonPath("$.reported").value(0))
                .andExpect(jsonPath("$.affectedMachines").value(0));
    }
}
