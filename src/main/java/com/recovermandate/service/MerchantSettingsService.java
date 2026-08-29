package com.recovermandate.service;

import com.recovermandate.audit.AuditService;
import com.recovermandate.dto.MerchantSettingsDto;
import com.recovermandate.entity.MerchantSettings;
import com.recovermandate.repository.MerchantSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantSettingsService {

    private final MerchantSettingsRepository merchantSettingsRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public MerchantSettingsDto getSettings() {
        MerchantSettings settings = getOrCreateEntity();
        return toDto(settings);
    }

    @Transactional
    public MerchantSettingsDto updateSettings(MerchantSettingsDto dto, String updatedBy) {
        MerchantSettings settings = getOrCreateEntity();

        if (dto.getDefaultTone() != null && !dto.getDefaultTone().isBlank()) {
            settings.setDefaultTone(dto.getDefaultTone().toLowerCase().trim());
        }
        settings.setAutoPilotEnabled(dto.isAutoPilotEnabled());
        if (dto.getAutoPilotMaxAmount() != null) {
            settings.setAutoPilotMaxAmount(Math.max(0L, dto.getAutoPilotMaxAmount()));
        }
        if (dto.getAutoPilotAllowedCategories() != null) {
            settings.setAutoPilotAllowedCategories(dto.getAutoPilotAllowedCategories().trim());
        }
        if (dto.getBusinessDisplayName() != null && !dto.getBusinessDisplayName().isBlank()) {
            settings.setBusinessDisplayName(dto.getBusinessDisplayName().trim());
        }
        settings.setUpdatedAt(Instant.now());

        MerchantSettings saved = merchantSettingsRepository.save(settings);

        String actor = (updatedBy != null && !updatedBy.isBlank()) ? updatedBy : "HUMAN";
        auditService.log(
                "MERCHANT_SETTINGS",
                saved.getId(),
                "SETTINGS_UPDATED",
                actor,
                String.format("Auto-Pilot=%s, MaxAmount=₹%.2f, DefaultTone=%s, Categories=%s",
                        saved.isAutoPilotEnabled(),
                        saved.getAutoPilotMaxAmount() / 100.0,
                        saved.getDefaultTone(),
                        saved.getAutoPilotAllowedCategories())
        );

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public boolean isAutoPilotEligible(Long amountPaise, String category) {
        if (amountPaise == null || category == null) {
            return false;
        }

        MerchantSettings settings = getOrCreateEntity();
        if (!settings.isAutoPilotEnabled()) {
            return false;
        }

        if (amountPaise > settings.getAutoPilotMaxAmount()) {
            return false;
        }

        Set<String> allowedCategories = Arrays.stream(settings.getAutoPilotAllowedCategories().split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        return allowedCategories.contains(category.toLowerCase().trim());
    }

    private MerchantSettings getOrCreateEntity() {
        return merchantSettingsRepository.findById(1L).orElseGet(() -> {
            MerchantSettings defaultSettings = MerchantSettings.builder()
                    .id(1L)
                    .defaultTone("balanced")
                    .autoPilotEnabled(false)
                    .autoPilotMaxAmount(250000L)
                    .autoPilotAllowedCategories("insufficient_funds,technical_decline")
                    .businessDisplayName("RecoverMandate Merchant")
                    .updatedAt(Instant.now())
                    .build();
            return merchantSettingsRepository.save(defaultSettings);
        });
    }

    private MerchantSettingsDto toDto(MerchantSettings settings) {
        return MerchantSettingsDto.builder()
                .defaultTone(settings.getDefaultTone())
                .autoPilotEnabled(settings.isAutoPilotEnabled())
                .autoPilotMaxAmount(settings.getAutoPilotMaxAmount())
                .autoPilotAllowedCategories(settings.getAutoPilotAllowedCategories())
                .businessDisplayName(settings.getBusinessDisplayName())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }
}
