package com.recovermandate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a Razorpay Payment Link generated for recovering a failed mandate.
 */
@Entity
@Table(
    name = "payment_links",
    indexes = {
        @jakarta.persistence.Index(name = "idx_payment_links_rzp_id", columnList = "razorpay_link_id", unique = true)
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_action_id", nullable = false)
    private RecoveryAction recoveryAction;

    @Column(name = "razorpay_link_id", nullable = false, unique = true)
    private String razorpayLinkId;

    @Column(name = "short_url", nullable = false)
    private String shortUrl;

    @Column(nullable = false)
    private Long amount; // in paise

    @Column(nullable = false)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "expire_by")
    private Instant expireBy;

    @Column(nullable = false)
    private String status; // CREATED, PAID, EXPIRED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "is_demo_data", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private boolean isDemoData = false;
}
