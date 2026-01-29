package com.ureclive.urec_live_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ureclive.urec_live_backend.dto.ChangePasswordRequest;
import com.ureclive.urec_live_backend.dto.UpdateEmailRequest;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@SuppressWarnings("null")
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        if (!userRepository.existsByUsername("testuser")) {
            userRepository.save(new User("testuser", "test@example.com", passwordEncoder.encode("password")));
        }
    }

    @Test
    @WithMockUser(username = "testuser")
    void getMeReturnsCurrentUser() throws Exception {
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void changePasswordSuccess() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("password", "newpassword");

        mockMvc.perform(post("/api/user/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        User updated = userRepository.findByUsername("testuser").orElseThrow();
        assertTrue(passwordEncoder.matches("newpassword", updated.getPassword()));
    }

    @Test
    @WithMockUser(username = "testuser")
    void changePasswordFailsWithWrongOldPassword() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("wrong", "newpassword");

        mockMvc.perform(post("/api/user/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()) // GlobalExceptionHandler maps RuntimeException to 400
                .andExpect(jsonPath("$.message").value("Invalid old password"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateEmailSuccess() throws Exception {
        UpdateEmailRequest request = new UpdateEmailRequest("new@example.com");

        mockMvc.perform(post("/api/user/update-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        User updated = userRepository.findByUsername("testuser").orElseThrow();
        assertEquals("new@example.com", updated.getEmail());
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateEmailFailsIfTaken() throws Exception {
        // Create another user
        userRepository.save(new User("other", "taken@example.com", "pass"));

        UpdateEmailRequest request = new UpdateEmailRequest("taken@example.com");

        mockMvc.perform(post("/api/user/update-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already taken"));
    }
}
