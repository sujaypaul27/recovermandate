package com.recovermandate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    private Long id;
    private UUID traceId;
    private String checksum;
    private String aiModelUsed;
    private String aiPromptHash;
    private String entityType;
    private Long entityId;
    private String action;
    private String actor;
    private String reasoning;
    private String details;
    private Instant createdAt;
    private Instant timestamp;
}
