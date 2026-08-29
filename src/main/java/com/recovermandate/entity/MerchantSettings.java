package com.recovermandate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity storing merchant-level autonomous recovery (Auto-Pilot) and tone preferences.
 *
 * NOTE [SINGLE-TENANT DEMO DESIGN CHOICE]:
 * For the Buildathon demo and single-store deployment mode, this entity utilizes a fixed
 * singleton record (id = 1L) initialized on startup. In multi-tenant production architectures,
 * this entity is scoped per merchant via `merchant_id` foreign key referencing {@link Merchant},
 * authenticated via Razorpay OAuth / Partner Sub-Merchant API tokens with per-tenant isolation.
 */
@Entity
@Table(name = "merchant_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSettings {

    @Id
    @Builder.Default
    private Long id = 1L;

    @Column(name = "default_tone", nullable = false)
    @Builder.Default
    private String defaultTone = "balanced";

    @Column(name = "auto_pilot_enabled", nullable = false)
    @Builder.Default
    private boolean autoPilotEnabled = false;

    @Column(name = "auto_pilot_max_amount", nullable = false)
    @Builder.Default
    private Long autoPilotMaxAmount = 250000L; // in paise, default ₹2,500.00

    @Column(name = "auto_pilot_allowed_categories", nullable = false)
    @Builder.Default
    private String autoPilotAllowedCategories = "insufficient_funds,technical_decline";

    @Column(name = "business_display_name", nullable = false)
    @Builder.Default
    private String businessDisplayName = "RecoverMandate Merchant";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
