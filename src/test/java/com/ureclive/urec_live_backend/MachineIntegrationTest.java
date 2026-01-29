package com.ureclive.urec_live_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ureclive.urec_live_backend.dto.CreateMachineRequest;
import com.ureclive.urec_live_backend.dto.UpdateMachineRequest;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SuppressWarnings("null")
public class MachineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        equipmentRepository.deleteAll();
    }

    @Test
    @WithMockUser
    void createMachineSuccess() throws Exception {
        CreateMachineRequest request = new CreateMachineRequest("NEW-MAC-001", "New Machine", "http://image.url");

        mockMvc.perform(post("/api/machines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("NEW-MAC-001")))
                .andExpect(jsonPath("$.name", is("New Machine")))
                .andExpect(jsonPath("$.status", is("AVAILABLE")));
    }

    @Test
    @WithMockUser
    void createMachineFailsIfCodeExists() throws Exception {
        // Given existing machine
        equipmentRepository.save(new Equipment("EXISTING-001", "Existing Machine", "AVAILABLE", null));

        CreateMachineRequest request = new CreateMachineRequest("EXISTING-001", "Duplicate Machine",
                "http://image.url");

        mockMvc.perform(post("/api/machines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()) // Correctly mapped by GlobalExceptionHandler
                .andExpect(content().string(containsString("already exists")));
    }

    @Test
    @WithMockUser
    void updateMachineSuccess() throws Exception {
        Equipment machine = equipmentRepository.save(new Equipment("UPD-001", "Old Name", "AVAILABLE", null));

        UpdateMachineRequest request = new UpdateMachineRequest("UPD-002", "New Name", "http://new.url");

        mockMvc.perform(put("/api/machines/" + machine.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("UPD-002")))
                .andExpect(jsonPath("$.name", is("New Name")));
    }

    @Test
    @WithMockUser
    void deleteMachineSuccess() throws Exception {
        Equipment machine = equipmentRepository.save(new Equipment("DEL-001", "Delete Me", "AVAILABLE", null));

        mockMvc.perform(delete("/api/machines/" + machine.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/machines/" + machine.getId()))
                .andExpect(status().isBadRequest()); // "Machine not found with ID" maps to 400
    }
}
