package com.recovermandate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity capturing a point-in-time health metrics snapshot for an issuer bank.
 */
@Entity
@Table(name = "bank_health_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankHealthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_code", nullable = false)
    private String bankCode; // e.g. HDFC, ICICI, SBI, AXIS, UNKNOWN

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "total_attempts", nullable = false)
    private int totalAttempts;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "failure_rate", nullable = false)
    private double failureRate; // 0.0 to 1.0

    @Column(nullable = false)
    private String status; // HEALTHY, DEGRADED, DOWN

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
