package com.ureclive.urec_live_backend;

import com.ureclive.urec_live_backend.dto.LoginRequest;
import com.ureclive.urec_live_backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@SuppressWarnings("null")
class ErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void loginWithBadCredentialsReturns401AndApiError() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new BadCredentialsException("Bad creds"));

        String json = "{\"username\": \"test\", \"password\": \"wrong\"}";

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void genericRuntimeErrorReturns400AndMessage() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("Something specific went wrong"));

        String json = "{\"username\": \"test\", \"password\": \"test\"}";

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Something specific went wrong"));
    }
}
