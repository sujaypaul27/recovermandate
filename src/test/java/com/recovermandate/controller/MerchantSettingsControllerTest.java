package com.recovermandate.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovermandate.dto.MerchantSettingsDto;
import com.recovermandate.service.MerchantSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
class MerchantSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MerchantSettingsService merchantSettingsService;

    @Test
    @DisplayName("GET /api/settings should return settings")
    void getSettings_returnsSettings() throws Exception {
        MerchantSettingsDto dto = MerchantSettingsDto.builder()
                .defaultTone("balanced")
                .autoPilotEnabled(true)
                .autoPilotMaxAmount(250000L)
                .autoPilotAllowedCategories("insufficient_funds,technical_decline")
                .businessDisplayName("Acme SaaS")
                .build();

        when(merchantSettingsService.getSettings()).thenReturn(dto);

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultTone").value("balanced"))
                .andExpect(jsonPath("$.autoPilotEnabled").value(true))
                .andExpect(jsonPath("$.autoPilotMaxAmount").value(250000))
                .andExpect(jsonPath("$.businessDisplayName").value("Acme SaaS"));
    }

    @Test
    @DisplayName("PUT /api/settings should update settings")
    void updateSettings_updatesSuccessfully() throws Exception {
        MerchantSettingsDto updateDto = MerchantSettingsDto.builder()
                .defaultTone("gentle")
                .autoPilotEnabled(true)
                .autoPilotMaxAmount(100000L)
                .autoPilotAllowedCategories("insufficient_funds")
                .businessDisplayName("Acme New")
                .build();

        when(merchantSettingsService.updateSettings(any(MerchantSettingsDto.class), eq("HUMAN")))
                .thenReturn(updateDto);

        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultTone").value("gentle"))
                .andExpect(jsonPath("$.autoPilotMaxAmount").value(100000))
                .andExpect(jsonPath("$.businessDisplayName").value("Acme New"));
    }
}
