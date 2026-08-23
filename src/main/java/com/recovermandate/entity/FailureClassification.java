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

@Entity
@Table(name = "failure_classifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureClassification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_event_id", nullable = false, unique = true)
    private PaymentEvent paymentEvent;

    @Column(nullable = false)
    private String category;

    @Column(name = "auto_recoverable", nullable = false)
    private boolean autoRecoverable;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}
