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
 * Entity logging multi-channel recovery communications dispatched to customers.
 */
@Entity
@Table(name = "dispatch_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_action_id", nullable = false)
    private RecoveryAction recoveryAction;

    @Column(nullable = false)
    private String channel; // EMAIL, WHATSAPP, SMS

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String status; // SENT, DELIVERED, FAILED

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;
}
