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

/**
 * Entity representing an automated retry attempt scheduled for a failed payment.
 */
@Entity
@Table(name = "retry_schedules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_event_id", nullable = false)
    private PaymentEvent paymentEvent;

    @Column(name = "failure_category", nullable = false)
    private String failureCategory;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(nullable = false)
    private String result; // PENDING, SUCCESS, FAILED, SKIPPED

    @Column(name = "razorpay_retry_payment_id")
    private String razorpayRetryPaymentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
