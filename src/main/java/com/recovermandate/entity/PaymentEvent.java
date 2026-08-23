package com.recovermandate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @Column(name = "razorpay_payment_id", unique = true, nullable = false)
    private String razorpayPaymentId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "failure_reason_code")
    private String failureReasonCode;

    private Long amount;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;
}
