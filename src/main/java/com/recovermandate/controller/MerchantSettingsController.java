package com.recovermandate.controller;

import com.recovermandate.dto.MerchantSettingsDto;
import com.recovermandate.service.MerchantSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller exposing merchant configuration and recovery policy settings
 * (e.g. auto-recovery thresholds, communication tone, and retry rules).
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class MerchantSettingsController {

    private final MerchantSettingsService merchantSettingsService;

    @GetMapping
    public ResponseEntity<MerchantSettingsDto> getSettings() {
        return ResponseEntity.ok(merchantSettingsService.getSettings());
    }

    @PutMapping
    public ResponseEntity<MerchantSettingsDto> updateSettings(@RequestBody MerchantSettingsDto dto) {
        return ResponseEntity.ok(merchantSettingsService.updateSettings(dto, "HUMAN"));
    }
}
