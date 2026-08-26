package com.recovermandate.service;

import com.recovermandate.dto.AuditLogResponse;
import com.recovermandate.entity.AuditLog;
import com.recovermandate.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;

    public Page<AuditLogResponse> getAuditLogs(String entityType, String actor, Instant startDate, Instant endDate, Pageable pageable) {
        Page<AuditLog> logs = auditLogRepository.findByFilters(entityType, actor, startDate, endDate, pageable);
        return logs.map(this::mapToResponse);
    }

    private AuditLogResponse mapToResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .action(log.getAction())
                .actor(log.getActor())
                .reasoning(log.getReasoning())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
