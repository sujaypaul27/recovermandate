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
@Table(name = "recovery_actions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "failure_classification_id", nullable = false, unique = true)
    private FailureClassification failureClassification;

    @Column(name = "ai_draft_message", columnDefinition = "TEXT")
    private String aiDraftMessage;

    @Column(name = "draft_source")
    private String draftSource;

    @Column(name = "payment_link_url")
    private String paymentLinkUrl;

    @Column(nullable = false)
    private String status;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "tone")
    private String tone;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private String actor;

    @jakarta.persistence.Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    @Builder.Default
    private Long version = 0L;

    public Long getVersion() {
        return version != null ? version : 0L;
    }
}
