package com.ureclive.urec_live_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentStatuses;
import com.ureclive.urec_live_backend.entity.Role;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentIssueReportRepository;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.RoleRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.security.CustomUserDetailsService;
import com.ureclive.urec_live_backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for API integration tests: boots the whole app (real controllers, JWT filter,
 * {@code @PreAuthorize}, validation and JPA queries) on in-memory H2 via the "test" profile, and
 * provides users with real JWTs, machines and request helpers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ApiIntegrationTestSupport {

    protected static final String MEMBER_API = "/api/equipment-issues";
    protected static final String ADMIN_API = "/api/admin/equipment-issues";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected EquipmentIssueReportRepository issueReportRepository;
    @Autowired protected EquipmentRepository equipmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TransactionTemplate transactionTemplate;

    /** Finds or creates a machine and resets it to Available, removed or not as asked. */
    protected Equipment machine(String code, String name, boolean deleted) {
        Equipment equipment = equipmentRepository.findByCode(code)
                .orElseGet(() -> new Equipment(code, name, EquipmentStatuses.AVAILABLE, null));
        equipment.setStatus(EquipmentStatuses.AVAILABLE);
        equipment.setDeleted(deleted);
        return equipmentRepository.save(equipment);
    }

    /** The machine's current status as stored, not the possibly stale copy the test holds. */
    protected String storedStatus(Equipment equipment) {
        return equipmentRepository.findById(equipment.getId()).orElseThrow().getStatus();
    }

    protected String tokenFor(String username, String roleName) {
        // One transaction so an existing Role stays managed when the new User cascades to it
        transactionTemplate.executeWithoutResult(tx -> {
            if (userRepository.findByUsername(username).isEmpty()) {
                Role role = roleRepository.findByName(roleName)
                        .orElseGet(() -> roleRepository.save(new Role(roleName)));
                User user = new User(username, username + "@example.com", passwordEncoder.encode("password"));
                user.addRole(role);
                userRepository.save(user);
            }
        });
        return jwtUtil.generateToken(userDetailsService.loadUserByUsername(username));
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected static Map<String, Object> fields(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    protected Map<String, Object> validReport(Equipment equipment) {
        return fields("equipmentId", equipment.getId(),
                      "severity", "DAMAGED",
                      "description", "Something is clearly wrong with it");
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    /** Files a report as the given member and returns its id. */
    protected long fileReport(String token, Equipment equipment, String severity) throws Exception {
        Map<String, Object> body = validReport(equipment);
        body.put("severity", severity);
        String response = mvc.perform(json(post(MEMBER_API).header(HttpHeaders.AUTHORIZATION, bearer(token)), body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    protected ResultActions getAs(String token, String url) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    protected ResultActions putStatusAs(String token, String url, String status) throws Exception {
        return putJsonAs(token, url, Map.of("status", status));
    }

    /** PUT with a JSON body; pass a null token to send the request without signing in. */
    protected ResultActions putJsonAs(String token, String url, Object body) throws Exception {
        MockHttpServletRequestBuilder request = put(url);
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        return mvc.perform(json(request, body));
    }
}
