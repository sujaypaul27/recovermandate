package com.recovermandate.audit;

import com.recovermandate.entity.AuditLog;
import com.recovermandate.repository.AuditLogRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for recording audit log events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

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
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType != null ? entityType : "UNKNOWN")
                .entityId(entityId != null ? entityId : 0L)
                .action(action != null ? action : "UNKNOWN")
                .actor(actor != null ? actor : "SYSTEM")
                .reasoning(reasoning)
                .createdAt(Instant.now())
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Audit log recorded: id={}, entityType={}, action={}, actor={}", 
                saved.getId(), entityType, action, actor);
        return saved;
    }
}
