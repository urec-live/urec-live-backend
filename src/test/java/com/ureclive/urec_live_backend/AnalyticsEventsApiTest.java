package com.ureclive.urec_live_backend;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentEvent;
import com.ureclive.urec_live_backend.entity.EquipmentEventType;
import com.ureclive.urec_live_backend.entity.EquipmentSession;
import com.ureclive.urec_live_backend.entity.EquipmentSessionStatus;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentEventRepository;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.EquipmentSessionRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsEventsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private EquipmentSessionRepository equipmentSessionRepository;

    @Autowired
    private EquipmentEventRepository equipmentEventRepository;

    @Test
    @Transactional
    void filtersEventsByEquipmentAndType() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = userRepository.save(new User("testuser_" + suffix, "testuser_" + suffix + "@example.com", "password"));

        Equipment equipmentA = equipmentRepository.save(
                new Equipment("EVT01_" + suffix, "Event Machine A", "AVAILABLE", null)
        );
        Equipment equipmentB = equipmentRepository.save(
                new Equipment("EVT02_" + suffix, "Event Machine B", "AVAILABLE", null)
        );

        EquipmentSession sessionA = new EquipmentSession();
        sessionA.setUser(user);
        sessionA.setEquipment(equipmentA);
        sessionA.setStatus(EquipmentSessionStatus.ACTIVE);
        sessionA.setStartedAt(Instant.now());
        sessionA = equipmentSessionRepository.save(sessionA);

        EquipmentSession sessionB = new EquipmentSession();
        sessionB.setUser(user);
        sessionB.setEquipment(equipmentB);
        sessionB.setStatus(EquipmentSessionStatus.ACTIVE);
        sessionB.setStartedAt(Instant.now());
        sessionB = equipmentSessionRepository.save(sessionB);

        EquipmentEvent eventA = new EquipmentEvent();
        eventA.setEventType(EquipmentEventType.SESSION_STARTED);
        eventA.setEquipment(equipmentA);
        eventA.setSession(sessionA);
        eventA.setUser(user);
        eventA.setOccurredAt(Instant.now());
        eventA.setMetadata("test_event_a");
        equipmentEventRepository.save(eventA);

        EquipmentEvent eventB = new EquipmentEvent();
        eventB.setEventType(EquipmentEventType.SESSION_STARTED);
        eventB.setEquipment(equipmentB);
        eventB.setSession(sessionB);
        eventB.setUser(user);
        eventB.setOccurredAt(Instant.now());
        eventB.setMetadata("test_event_b");
        equipmentEventRepository.save(eventB);

        EquipmentEvent eventAEnded = new EquipmentEvent();
        eventAEnded.setEventType(EquipmentEventType.SESSION_ENDED);
        eventAEnded.setEquipment(equipmentA);
        eventAEnded.setSession(sessionA);
        eventAEnded.setUser(user);
        eventAEnded.setOccurredAt(Instant.now());
        eventAEnded.setMetadata("test_event_a_end");
        equipmentEventRepository.save(eventAEnded);

        mockMvc.perform(get("/api/analytics/events")
                        .param("equipmentId", String.valueOf(equipmentA.getId()))
                        .param("eventType", EquipmentEventType.SESSION_STARTED.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentA.getId()))
                .andExpect(jsonPath("$.content[0].eventType").value(EquipmentEventType.SESSION_STARTED.name()));
    }

    @Test
    @Transactional
    void eventsSizeIsCappedAt200() throws Exception {
        mockMvc.perform(get("/api/analytics/events")
                        .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(200));
    }

    @Test
    @Transactional
    void rollingUtilizationIncludesActiveSessions() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = userRepository.save(new User("util_user_" + suffix, "util_" + suffix + "@example.com", "password"));
        Equipment equipment = equipmentRepository.save(
                new Equipment("UTIL01_" + suffix, "Util Machine", "AVAILABLE", null)
        );

        EquipmentSession session = new EquipmentSession();
        session.setUser(user);
        session.setEquipment(equipment);
        session.setStatus(EquipmentSessionStatus.ACTIVE);
        session.setStartedAt(Instant.now().minus(Duration.ofMinutes(5)));
        equipmentSessionRepository.save(session);

        MvcResult result = mockMvc.perform(get("/api/analytics/utilization/rolling")
                        .param("minutes", "15"))
                .andExpect(status().isOk())
                .andReturn();
        String payload = result.getResponse().getContentAsString();
        List<Double> utilizationValues = JsonPath.read(
                payload,
                "$[?(@.code=='" + equipment.getCode() + "')].utilizationPercent"
        );
        List<Integer> equipmentIds = JsonPath.read(
                payload,
                "$[?(@.code=='" + equipment.getCode() + "')].equipmentId"
        );
        List<String> windowStarts = JsonPath.read(
                payload,
                "$[?(@.code=='" + equipment.getCode() + "')].windowStart"
        );
        List<String> windowEnds = JsonPath.read(
                payload,
                "$[?(@.code=='" + equipment.getCode() + "')].windowEnd"
        );
        assertThat(utilizationValues).isNotNull();
        assertThat(utilizationValues).hasSize(1);
        assertThat(utilizationValues.get(0)).isGreaterThan(0.0).isLessThanOrEqualTo(100.0);
        assertThat(equipmentIds).containsExactly(equipment.getId().intValue());
        assertThat(windowStarts).hasSize(1);
        assertThat(windowEnds).hasSize(1);
        Instant windowStart = Instant.parse(windowStarts.get(0));
        Instant windowEnd = Instant.parse(windowEnds.get(0));
        assertThat(windowEnd).isAfter(windowStart);
    }

    @Test
    @Transactional
    void rollingUtilizationDefaultsWhenMinutesIsZero() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = userRepository.save(new User("util_zero_user_" + suffix, "util_zero_" + suffix + "@example.com", "password"));
        Equipment equipment = equipmentRepository.save(
                new Equipment("UTIL_ZERO_" + suffix, "Util Zero Machine", "AVAILABLE", null)
        );

        EquipmentSession session = new EquipmentSession();
        session.setUser(user);
        session.setEquipment(equipment);
        session.setStatus(EquipmentSessionStatus.ACTIVE);
        session.setStartedAt(Instant.now().minus(Duration.ofMinutes(5)));
        equipmentSessionRepository.save(session);

        MvcResult result = mockMvc.perform(get("/api/analytics/utilization/rolling")
                        .param("minutes", "0"))
                .andExpect(status().isOk())
                .andReturn();
        String payload = result.getResponse().getContentAsString();
        List<Double> utilizationValues = JsonPath.read(
                payload,
                "$[?(@.code=='" + equipment.getCode() + "')].utilizationPercent"
        );
        assertThat(utilizationValues).hasSize(1);
        assertThat(utilizationValues.get(0)).isGreaterThan(0.0);
    }

    @Test
    @Transactional
    void waitTimeShowsEstimateForInUseEquipment() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = userRepository.save(new User("wait_user_" + suffix, "wait_" + suffix + "@example.com", "password"));
        Equipment equipment = equipmentRepository.save(
                new Equipment("WAIT01_" + suffix, "Wait Machine", "AVAILABLE", null)
        );

        long[] durations = { 300, 360, 420, 480, 540 };
        Instant historyBase = Instant.now().minus(Duration.ofHours(2));
        for (int i = 0; i < durations.length; i++) {
            EquipmentSession historySession = new EquipmentSession();
            historySession.setUser(user);
            historySession.setEquipment(equipment);
            historySession.setStatus(EquipmentSessionStatus.ENDED);
            Instant startedAt = historyBase.plusSeconds(i * 60L);
            historySession.setStartedAt(startedAt);
            historySession.setEndedAt(startedAt.plusSeconds(durations[i]));
            equipmentSessionRepository.save(historySession);
        }

        EquipmentSession activeSession = new EquipmentSession();
        activeSession.setUser(user);
        activeSession.setEquipment(equipment);
        activeSession.setStatus(EquipmentSessionStatus.ACTIVE);
        activeSession.setStartedAt(Instant.now().minus(Duration.ofMinutes(2)));
        equipmentSessionRepository.save(activeSession);

        MvcResult waitResult = mockMvc.perform(get("/api/analytics/wait-time/{code}", equipment.getCode())
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andReturn();
        String waitPayload = waitResult.getResponse().getContentAsString();
        Boolean inUse = JsonPath.read(waitPayload, "$.inUse");
        Integer average = JsonPath.read(waitPayload, "$.averageDurationSeconds");
        Integer estimated = JsonPath.read(waitPayload, "$.estimatedWaitSeconds");
        assertThat(inUse).isTrue();
        assertThat(average).isEqualTo(420);
        assertThat(estimated).isNotNull();
        assertThat(estimated).isBetween(0, 420);
    }

    @Test
    @Transactional
    void dataQualityAuditIncludesSessionsMissingEvents() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = userRepository.save(new User("audit_user_" + suffix, "audit_" + suffix + "@example.com", "password"));
        Equipment equipment = equipmentRepository.save(
                new Equipment("AUDIT01_" + suffix, "Audit Machine", "AVAILABLE", null)
        );

        EquipmentSession session = new EquipmentSession();
        session.setUser(user);
        session.setEquipment(equipment);
        session.setStatus(EquipmentSessionStatus.ACTIVE);
        session.setStartedAt(Instant.now());
        session = equipmentSessionRepository.save(session);

        MvcResult result = mockMvc.perform(get("/api/analytics/audit")
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andReturn();

        String payload = result.getResponse().getContentAsString();
        Integer missingCount = JsonPath.read(payload, "$.sessionsMissingEvents");
        List<Integer> sessionIds = JsonPath.read(payload, "$.sessionIdsMissingEvents");
        assertThat(missingCount).isGreaterThanOrEqualTo(1);
        assertThat(sessionIds).contains(session.getId().intValue());
    }

    @Test
    @Transactional
    void dataQualityAuditFlagsOutOfOrderEvents() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = userRepository.save(new User("audit_order_user_" + suffix, "audit_order_" + suffix + "@example.com", "password"));
        Equipment equipment = equipmentRepository.save(
                new Equipment("AUDIT02_" + suffix, "Audit Order Machine", "AVAILABLE", null)
        );

        EquipmentSession session = new EquipmentSession();
        session.setUser(user);
        session.setEquipment(equipment);
        session.setStatus(EquipmentSessionStatus.ACTIVE);
        session.setStartedAt(Instant.now());
        session = equipmentSessionRepository.save(session);

        EquipmentEvent endEvent = new EquipmentEvent();
        endEvent.setEventType(EquipmentEventType.SESSION_ENDED);
        endEvent.setEquipment(equipment);
        endEvent.setSession(session);
        endEvent.setUser(user);
        endEvent.setOccurredAt(Instant.now().minus(Duration.ofMinutes(10)));
        endEvent.setMetadata("audit_end_before_start");
        equipmentEventRepository.save(endEvent);

        EquipmentEvent startEvent = new EquipmentEvent();
        startEvent.setEventType(EquipmentEventType.SESSION_STARTED);
        startEvent.setEquipment(equipment);
        startEvent.setSession(session);
        startEvent.setUser(user);
        startEvent.setOccurredAt(Instant.now().minus(Duration.ofMinutes(5)));
        startEvent.setMetadata("audit_start_after_end");
        equipmentEventRepository.save(startEvent);

        MvcResult result = mockMvc.perform(get("/api/analytics/audit")
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andReturn();

        String payload = result.getResponse().getContentAsString();
        Integer outOfOrderCount = JsonPath.read(payload, "$.sessionsWithOutOfOrderEvents");
        List<Integer> sessionIds = JsonPath.read(payload, "$.sessionIdsWithOutOfOrderEvents");
        assertThat(outOfOrderCount).isGreaterThanOrEqualTo(1);
        assertThat(sessionIds).contains(session.getId().intValue());
    }
}
