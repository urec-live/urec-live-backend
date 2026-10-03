package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentStatuses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Out-of-order machines end to end: the admin switch, the Equipment page, the member check-in rules
 * and the automatic return to service when the last open report is resolved.
 */
class OutOfOrderIntegrationTest extends ApiIntegrationTestSupport {

    private static final String OUT_OF_ORDER = EquipmentStatuses.OUT_OF_ORDER;
    private static final String AVAILABLE = EquipmentStatuses.AVAILABLE;

    private Equipment legPress;
    private Equipment bench;

    private String memberToken;
    private String otherMemberToken;
    private String adminToken;          // role "ADMIN", which the admin dashboard assigns
    private String prefixedAdminToken;  // role "ROLE_ADMIN"

    @BeforeEach
    void setUp() {
        issueReportRepository.deleteAll();

        legPress = machine("OO-LP", "OO Leg Press", false);
        bench = machine("OO-BP", "OO Bench Press", false);

        memberToken = tokenFor("oo_member", "USER");
        otherMemberToken = tokenFor("oo_member2", "USER");
        adminToken = tokenFor("oo_admin", "ADMIN");
        prefixedAdminToken = tokenFor("oo_admin_prefixed", "ROLE_ADMIN");
    }

    // ── The admin switch ──────────────────────────────────────────────────────

    @Test
    void adminsWithEitherRoleNameCanTakeAMachineOutOfServiceAndBringItBack() throws Exception {
        setOutOfOrder(adminToken, legPress, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(legPress.getId().intValue()))
                .andExpect(jsonPath("$.status").value(OUT_OF_ORDER));
        // Members see it on the public machine endpoints
        mvc.perform(get("/api/machines/" + legPress.getId())).andExpect(jsonPath("$.status").value(OUT_OF_ORDER));

        setOutOfOrder(prefixedAdminToken, legPress, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(AVAILABLE));
        assertEquals(AVAILABLE, storedStatus(legPress));
    }

    @Test
    void membersCantUseTheAdminSwitch() throws Exception {
        setOutOfOrder(memberToken, legPress, true).andExpect(status().isForbidden());
        putJsonAs(null, outOfOrderUrl(legPress), Map.of("outOfOrder", true)).andExpect(status().isUnauthorized());

        assertEquals(AVAILABLE, storedStatus(legPress));
    }

    @Test
    void theSwitchRejectsBadRequestsAndUnknownMachines() throws Exception {
        putJsonAs(adminToken, outOfOrderUrl(legPress), Map.of()).andExpect(status().isBadRequest());
        putJsonAs(adminToken, ADMIN_API + "/equipment/999999/out-of-order", Map.of("outOfOrder", true))
                .andExpect(status().isNotFound());
        Equipment removed = machine("OO-RM", "OO Removed Rower", true);
        setOutOfOrder(adminToken, removed, true).andExpect(status().isNotFound());

        assertEquals(AVAILABLE, storedStatus(legPress));
    }

    @Test
    void bringingAMachineBackLeavesAnInUseMachineInUse() throws Exception {
        memberSetsStatus("/" + legPress.getId(), "In Use").andExpect(status().isOk());

        setOutOfOrder(adminToken, legPress, false).andExpect(jsonPath("$.status").value("In Use"));

        assertEquals("In Use", storedStatus(legPress));
    }

    // ── Member check-in and check-out ─────────────────────────────────────────

    @Test
    void checkingInToAnOutOfOrderMachineIsRefused() throws Exception {
        setOutOfOrder(adminToken, legPress, true).andExpect(status().isOk());

        memberSetsStatus("/" + legPress.getId(), "In Use").andExpect(status().isConflict());
        memberSetsStatus("/code/" + legPress.getCode(), "In Use").andExpect(status().isConflict());
        // These endpoints don't require a login, so check that path too (and the case-insensitive match)
        putJsonAs(null, "/api/machines/code/" + legPress.getCode() + "/status", Map.of("status", "in use"))
                .andExpect(status().isConflict());

        assertEquals(OUT_OF_ORDER, storedStatus(legPress));
    }

    @Test
    void checkingOutOfAMachineThatWentOutOfOrderStillEndsTheWorkout() throws Exception {
        memberSetsStatus("/code/" + legPress.getCode(), "In Use").andExpect(status().isOk());
        setOutOfOrder(adminToken, legPress, true).andExpect(status().isOk());

        memberSetsStatus("/code/" + legPress.getCode(), "Available")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OUT_OF_ORDER));

        assertEquals(OUT_OF_ORDER, storedStatus(legPress));
    }

    @Test
    void membersCantMarkAMachineOutOfOrderThemselves() throws Exception {
        memberSetsStatus("/" + legPress.getId(), "Out of Order").andExpect(status().isForbidden());
        memberSetsStatus("/code/" + legPress.getCode(), "  out of order ").andExpect(status().isForbidden());
        putJsonAs(null, "/api/machines/" + legPress.getId() + "/status", Map.of("status", "OUT OF ORDER"))
                .andExpect(status().isForbidden());

        assertEquals(AVAILABLE, storedStatus(legPress));
    }

    @Test
    void ordinaryCheckInAndOutStillWork() throws Exception {
        memberSetsStatus("/code/" + bench.getCode(), "In Use")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("In Use"));
        memberSetsStatus("/code/" + bench.getCode(), "Available")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(AVAILABLE));
    }

    // ── Equipment page ────────────────────────────────────────────────────────

    @Test
    void theEquipmentPageCanTakeAMachineOutOfServiceAndBringItBack() throws Exception {
        putJsonAs(prefixedAdminToken, "/api/admin/equipment/" + bench.getId(), Map.of("status", OUT_OF_ORDER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OUT_OF_ORDER));
        memberSetsStatus("/" + bench.getId(), "In Use").andExpect(status().isConflict());

        putJsonAs(prefixedAdminToken, "/api/admin/equipment/" + bench.getId(), Map.of("status", AVAILABLE))
                .andExpect(status().isOk());
        memberSetsStatus("/" + bench.getId(), "In Use").andExpect(status().isOk());
    }

    // ── Back in service once fixed ────────────────────────────────────────────

    @Test
    void resolvingTheLastOpenReportPutsTheMachineBackInService() throws Exception {
        long reportId = fileReport(memberToken, legPress, "OUT_OF_ORDER");
        setOutOfOrder(adminToken, legPress, true).andExpect(status().isOk());

        putStatusAs(adminToken, ADMIN_API + "/" + reportId + "/status", "RESOLVED").andExpect(status().isOk());

        assertEquals(AVAILABLE, storedStatus(legPress));
        memberSetsStatus("/" + legPress.getId(), "In Use").andExpect(status().isOk());
    }

    @Test
    void theMachineStaysOutOfOrderWhileAnotherReportIsOpen() throws Exception {
        long first = fileReport(memberToken, legPress, "OUT_OF_ORDER");
        long second = fileReport(otherMemberToken, legPress, "DAMAGED");
        setOutOfOrder(adminToken, legPress, true).andExpect(status().isOk());

        putStatusAs(adminToken, ADMIN_API + "/" + first + "/status", "RESOLVED").andExpect(status().isOk());
        assertEquals(OUT_OF_ORDER, storedStatus(legPress));

        putStatusAs(adminToken, ADMIN_API + "/" + second + "/status", "RESOLVED").andExpect(status().isOk());
        assertEquals(AVAILABLE, storedStatus(legPress));
    }

    @Test
    void resolvingEveryOpenReportAtOncePutsTheMachineBackInService() throws Exception {
        fileReport(memberToken, legPress, "OUT_OF_ORDER");
        fileReport(otherMemberToken, legPress, "DAMAGED");
        setOutOfOrder(adminToken, legPress, true).andExpect(status().isOk());

        putStatusAs(adminToken, ADMIN_API + "/equipment/" + legPress.getId() + "/status", "RESOLVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openReportCount").value(0))
                .andExpect(jsonPath("$.equipmentStatus").value(AVAILABLE));

        assertEquals(AVAILABLE, storedStatus(legPress));
    }

    @Test
    void resolvingDoesNotChangeAMachineThatIsNotOutOfOrder() throws Exception {
        long reportId = fileReport(memberToken, legPress, "DAMAGED");
        memberSetsStatus("/" + legPress.getId(), "In Use").andExpect(status().isOk());

        putStatusAs(adminToken, ADMIN_API + "/" + reportId + "/status", "RESOLVED").andExpect(status().isOk());

        assertEquals("In Use", storedStatus(legPress));
    }

    @Test
    void reopeningAReportDoesNotTakeTheMachineOutOfService() throws Exception {
        long reportId = fileReport(memberToken, legPress, "OUT_OF_ORDER");
        putStatusAs(adminToken, ADMIN_API + "/" + reportId + "/status", "RESOLVED").andExpect(status().isOk());

        putStatusAs(adminToken, ADMIN_API + "/" + reportId + "/status", "IN_PROGRESS").andExpect(status().isOk());

        assertEquals(AVAILABLE, storedStatus(legPress));
    }

    // ── Issues view ───────────────────────────────────────────────────────────

    @Test
    void theIssuesViewShowsEachMachinesStatusAndCountsOutOfOrderMachines() throws Exception {
        long outOfOrderBefore = outOfOrderMachines();
        fileReport(memberToken, legPress, "OUT_OF_ORDER");
        fileReport(memberToken, bench, "DAMAGED");
        setOutOfOrder(adminToken, legPress, true).andExpect(status().isOk());
        // A removed machine that's out of order isn't counted
        Equipment removed = machine("OO-RM", "OO Removed Rower", true);
        removed.setStatus(OUT_OF_ORDER);
        equipmentRepository.save(removed);

        getAs(adminToken, ADMIN_API)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].equipmentName").value("OO Leg Press"))
                .andExpect(jsonPath("$[0].equipmentStatus").value(OUT_OF_ORDER))
                .andExpect(jsonPath("$[1].equipmentName").value("OO Bench Press"))
                .andExpect(jsonPath("$[1].equipmentStatus").value(AVAILABLE));
        assertEquals(outOfOrderBefore + 1, outOfOrderMachines());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String outOfOrderUrl(Equipment equipment) {
        return ADMIN_API + "/equipment/" + equipment.getId() + "/out-of-order";
    }

    private ResultActions setOutOfOrder(String token, Equipment equipment, boolean outOfOrder) throws Exception {
        return putJsonAs(token, outOfOrderUrl(equipment), Map.of("outOfOrder", outOfOrder));
    }

    /** The member-facing check-in/check-out endpoint, as the app calls it. */
    private ResultActions memberSetsStatus(String machinePath, String status) throws Exception {
        return putJsonAs(memberToken, "/api/machines" + machinePath + "/status", Map.of("status", status));
    }

    private long outOfOrderMachines() throws Exception {
        String summary = getAs(adminToken, ADMIN_API + "/summary").andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(summary).get("outOfOrderMachines").asLong();
    }
}
