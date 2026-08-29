package com.recovermandate.service;

import com.recovermandate.audit.AuditService;
import com.recovermandate.dto.MerchantSettingsDto;
import com.recovermandate.entity.MerchantSettings;
import com.recovermandate.repository.MerchantSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantSettingsServiceTest {

    @Mock
    private MerchantSettingsRepository merchantSettingsRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private MerchantSettingsService merchantSettingsService;

    private MerchantSettings settings;

    @BeforeEach
    void setUp() {
        settings = MerchantSettings.builder()
                .id(1L)
                .defaultTone("balanced")
                .autoPilotEnabled(true)
                .autoPilotMaxAmount(250000L) // ₹2,500.00
                .autoPilotAllowedCategories("insufficient_funds,technical_decline")
                .businessDisplayName("Acme Subscriptions")
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should return existing settings DTO")
    void getSettings_returnsDto() {
        when(merchantSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));

        MerchantSettingsDto dto = merchantSettingsService.getSettings();

        assertNotNull(dto);
        assertEquals("balanced", dto.getDefaultTone());
        assertTrue(dto.isAutoPilotEnabled());
        assertEquals(250000L, dto.getAutoPilotMaxAmount());
        assertEquals("Acme Subscriptions", dto.getBusinessDisplayName());
    }

    @Test
    @DisplayName("Should update settings and record audit log")
    void updateSettings_updatesAndAudits() {
        when(merchantSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(merchantSettingsRepository.save(any(MerchantSettings.class))).thenAnswer(i -> i.getArgument(0));

        MerchantSettingsDto updateDto = MerchantSettingsDto.builder()
                .defaultTone("urgent")
                .autoPilotEnabled(false)
                .autoPilotMaxAmount(500000L)
                .autoPilotAllowedCategories("insufficient_funds")
                .businessDisplayName("Acme Corp Global")
                .build();

        MerchantSettingsDto result = merchantSettingsService.updateSettings(updateDto, "ADMIN_USER");

        assertNotNull(result);
        assertEquals("urgent", result.getDefaultTone());
        assertFalse(result.isAutoPilotEnabled());
        assertEquals(500000L, result.getAutoPilotMaxAmount());
        assertEquals("Acme Corp Global", result.getBusinessDisplayName());

        verify(merchantSettingsRepository).save(any(MerchantSettings.class));
        verify(auditService).log(eq("MERCHANT_SETTINGS"), eq(1L), eq("SETTINGS_UPDATED"), eq("ADMIN_USER"), anyString());
    }

    @Test
    @DisplayName("Should correctly evaluate auto-pilot eligibility based on amount and category")
    void isAutoPilotEligible_evaluatesCorrectly() {
        when(merchantSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));

        // Eligible: ₹1,500 (< ₹2,500) and insufficient_funds
        assertTrue(merchantSettingsService.isAutoPilotEligible(150000L, "insufficient_funds"));

        // Eligible: ₹2,500 (== ₹2,500) and technical_decline
        assertTrue(merchantSettingsService.isAutoPilotEligible(250000L, "technical_decline"));

        // Ineligible: Amount exceeds limit (₹3,000 > ₹2,500)
        assertFalse(merchantSettingsService.isAutoPilotEligible(300000L, "insufficient_funds"));

        // Ineligible: Disallowed category (expired_mandate)
        assertFalse(merchantSettingsService.isAutoPilotEligible(100000L, "expired_mandate"));

        // Ineligible: AutoPilot disabled
        settings.setAutoPilotEnabled(false);
        assertFalse(merchantSettingsService.isAutoPilotEligible(100000L, "insufficient_funds"));
    }
}
