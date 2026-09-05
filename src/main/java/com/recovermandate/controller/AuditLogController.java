package com.recovermandate.controller;

import com.recovermandate.audit.AuditService;
import com.recovermandate.dto.AuditChainVerificationResponse;
import com.recovermandate.dto.AuditLogResponse;
import com.recovermandate.service.AuditQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Controller providing query and verification endpoints for the cryptographic SHA-256
 * tamper-evident audit ledger.
 */
@RestController
@RequestMapping("/api/audit-log")
@RequiredArgsConstructor
@Validated
public class AuditLogController {

    private final AuditQueryService auditQueryService;
    private final AuditService auditService;

    @GetMapping
    public Page<AuditLogResponse> getAuditLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        
        return auditQueryService.getAuditLogs(
                entityType,
                actor,
                startDate,
                endDate,
                PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id"))
        );
    }

    @GetMapping("/verify-chain")
    public AuditChainVerificationResponse verifyChain() {
        return auditService.verifyChain();
    }

    @org.springframework.web.bind.annotation.PostMapping("/reseal-chain")
    public AuditChainVerificationResponse resealChain() {
        return auditService.resealChain();
    }
}
