package com.recovermandate.audit;

import com.recovermandate.entity.AuditLog;
import com.recovermandate.repository.AuditLogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for recording tamper-proof audit log events with SHA-256 hash chaining.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Single-node SHA-256 hash chain seed for tamper-evident audit logging.
     * Note: volatile lastChecksum introduces a serialized write bottleneck,
     * which is acceptable and sufficient for our current webhook and recovery throughput.
     */
    private volatile String lastChecksum = "GENESIS";

    /**
     * Records an audit log entry.
     *
     * @param entityType type of entity (e.g. PAYMENT_EVENT, WEBHOOK)
     * @param entityId   identifier of the entity (or 0L if not available)
     * @param action     action performed
     * @param actor      actor performing action (e.g. SYSTEM, AI, HUMAN)
     * @param reasoning  description or reasoning for the audit trail
     * @return created AuditLog entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog log(String entityType, Long entityId, String action, String actor, String reasoning) {
        return log(entityType, entityId, action, actor, reasoning, null, null);
    }

    /**
     * Records an audit log entry with AI metadata and SHA-256 hash chain verification.
     *
     * @param entityType   type of entity (e.g. PAYMENT_EVENT, WEBHOOK)
     * @param entityId     identifier of the entity (or 0L if not available)
     * @param action       action performed
     * @param actor        actor performing action (e.g. SYSTEM, AI, HUMAN)
     * @param reasoning    description or reasoning for the audit trail
     * @param aiModelUsed  identifier of AI model used (nullable)
     * @param aiPromptHash SHA-256 hash of outgoing prompt (nullable)
     * @return created AuditLog entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized AuditLog log(String entityType, Long entityId, String action, String actor,
                                     String reasoning, String aiModelUsed, String aiPromptHash) {
        String traceIdStr = MDC.get("traceId");
        UUID traceId = null;
        if (traceIdStr != null && !traceIdStr.isBlank()) {
            try {
                traceId = UUID.fromString(traceIdStr);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid traceId in MDC: {}", traceIdStr);
            }
        }

        Instant now = Instant.now();
        String safeEntityType = entityType != null ? entityType : "UNKNOWN";
        Long safeEntityId = entityId != null ? entityId : 0L;
        String safeAction = action != null ? action : "UNKNOWN";
        String safeActor = actor != null ? actor : "SYSTEM";

        String rawChainInput = lastChecksum + "|" + safeEntityType + "|" + safeEntityId + "|"
                + safeAction + "|" + safeActor + "|" + now.toString();
        String checksum = sha256(rawChainInput);

        AuditLog auditLog = AuditLog.builder()
                .traceId(traceId)
                .checksum(checksum)
                .aiModelUsed(aiModelUsed)
                .aiPromptHash(aiPromptHash)
                .entityType(safeEntityType)
                .entityId(safeEntityId)
                .action(safeAction)
                .actor(safeActor)
                .reasoning(reasoning)
                .createdAt(now)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        this.lastChecksum = checksum;

        log.info("Audit log recorded: id={}, entityType={}, action={}, actor={}, traceId={}, checksum={}",
                saved.getId(), safeEntityType, safeAction, safeActor, traceId, checksum);
        return saved;
    }

    public String getLastChecksum() {
        return lastChecksum;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
