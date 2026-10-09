package com.ureclive.urec_live_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.entity.Role;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.ExerciseRepository;
import com.ureclive.urec_live_backend.repository.HelpRequestRepository;
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
 * Shared setup for help request API tests: boots the whole app (real controllers, JWT filter,
 * {@code @PreAuthorize}, validation and JPA) on in-memory H2 via the "test" profile, and provides
 * users with real JWTs, machines and request helpers. Same annotations as the other API test
 * bases, so they all share one cached Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class HelpRequestApiTestSupport {

    protected static final String MEMBER_API = "/api/help-requests";
    protected static final String ADMIN_API = "/api/admin/help-requests";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected HelpRequestRepository helpRequestRepository;
    @Autowired protected EquipmentRepository equipmentRepository;
    @Autowired protected ExerciseRepository exerciseRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired protected TransactionTemplate transactionTemplate;
    @Autowired private JwtUtil jwtUtil;

    /**
     * Finds or creates a test machine (codes start with "HR-" so they never clash with the seeded
     * BP001 and friends), linked to the given exercise if one is named.
     */
    protected Equipment machine(String code, String name, boolean deleted, String exerciseName) {
        return transactionTemplate.execute(tx -> {
            Equipment equipment = equipmentRepository.findByCode(code)
                    .orElseGet(() -> new Equipment(code, name, "Available", null));
            equipment.setDeleted(deleted);
            if (exerciseName != null && equipment.getExercises().stream()
                    .noneMatch(e -> e.getName().equals(exerciseName))) {
                Exercise exercise = exerciseRepository.findByNameIgnoreCase(exerciseName)
                        .orElseGet(() -> exerciseRepository.save(new Exercise(exerciseName, "Test", null)));
                equipment.addExercise(exercise);
            }
            return equipmentRepository.save(equipment);
        });
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

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    /** Calls staff to the machine as the given member and returns the new request's id. */
    protected long callStaff(String token, Equipment equipment) throws Exception {
        String response = postJsonAs(token, MEMBER_API, fields("equipmentId", equipment.getId()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** GET; pass a null token to send the request without signing in. */
    protected ResultActions getAs(String token, String url) throws Exception {
        return mvc.perform(withToken(get(url), token));
    }

    /** POST with no body; pass a null token to send the request without signing in. */
    protected ResultActions postAs(String token, String url) throws Exception {
        return mvc.perform(withToken(post(url), token));
    }

    protected ResultActions postJsonAs(String token, String url, Object body) throws Exception {
        return mvc.perform(json(withToken(post(url), token), body));
    }

    protected ResultActions putStatusAs(String token, long id, String status) throws Exception {
        return mvc.perform(json(withToken(put(ADMIN_API + "/" + id + "/status"), token), fields("status", status)));
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, bearer(token));
    }
}
