package com.ureclive.urec_live_backend;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.EquipmentSessionRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@SuppressWarnings("null")
class SessionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private EquipmentSessionRepository sessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Equipment testEquipment;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        equipmentRepository.deleteAll();
        userRepository.deleteAll();

        if (!userRepository.existsByUsername("testuser")) {
            userRepository.save(new User("testuser", "test@example.com", passwordEncoder.encode("password")));
        }

        testEquipment = new Equipment("TEST-BP-001", "Test Bench Press", "AVAILABLE", "http://example.com/img.png");
        System.out.println("Saving equipment with status: " + testEquipment.getStatus());
        testEquipment = equipmentRepository.save(testEquipment);
    }

    @Test
    @WithMockUser(username = "testuser")
    // ^ Mocking the authentication context directly for simplicity, since we tested
    // Auth flow separately
    void startSessionByQrCodeSuccess() throws Exception {
        mockMvc.perform(post("/api/equipment-sessions/start/code/" + testEquipment.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.equipment.code").value("TEST-BP-001"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void startSessionByIdSuccess() throws Exception {
        mockMvc.perform(post("/api/equipment-sessions/start/" + testEquipment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void endSessionByIdSuccess() throws Exception {
        // Start first
        mockMvc.perform(post("/api/equipment-sessions/start/" + testEquipment.getId()))
                .andExpect(status().isOk());

        // Then End
        mockMvc.perform(post("/api/equipment-sessions/end/" + testEquipment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));
    }
}
