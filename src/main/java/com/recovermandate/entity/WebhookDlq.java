package com.recovermandate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity representing rejected, unparseable, or signature-failed webhooks
 * captured in the Dead-Letter Queue (DLQ) for forensic inspection and replay.
 */
@Entity
@Table(name = "webhook_dlq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDlq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(columnDefinition = "TEXT")
    private String headers;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    @Builder.Default
    private String status = "REJECTED"; // REJECTED, REPLAYED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "replayed_at")
    private Instant replayedAt;
}
