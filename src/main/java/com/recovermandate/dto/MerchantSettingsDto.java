package com.recovermandate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSettingsDto {
    @Builder.Default
    private String defaultTone = "balanced";

    @Builder.Default
    private boolean autoPilotEnabled = false;

    @Builder.Default
    private Long autoPilotMaxAmount = 250000L; // in paise, default ₹2,500.00

    @Builder.Default
    private String autoPilotAllowedCategories = "insufficient_funds,technical_decline";

    @Builder.Default
    private String businessDisplayName = "RecoverMandate Merchant";

    private Instant updatedAt;
}
