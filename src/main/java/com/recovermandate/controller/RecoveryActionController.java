package com.recovermandate.controller;

import com.recovermandate.dto.RejectActionRequest;
import com.recovermandate.service.RecoveryActionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller managing the operator approval queue for AI-generated recovery actions,
 * supporting single and batch approval, rejection, and immediate dispatch.
 */
@RestController
@RequestMapping("/api/recovery-actions")
@RequiredArgsConstructor
@Validated
public class RecoveryActionController {

    private final RecoveryActionService recoveryActionService;

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approveAction(@PathVariable Long id) {
        // Assume authenticated user is known. Using "HUMAN" for now since no auth is configured.
        recoveryActionService.approveAction(id, "HUMAN");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/approve-and-dispatch")
    public ResponseEntity<Void> approveAndDispatch(
            @PathVariable Long id,
            @RequestBody(required = false) com.recovermandate.dto.ApproveActionRequest request) {
        String approvedBy = (request != null && request.getApprovedBy() != null && !request.getApprovedBy().isBlank())
                ? request.getApprovedBy()
                : "HUMAN";
        String tone = (request != null) ? request.getTone() : null;
        String message = (request != null) ? request.getMessage() : null;

        if (tone != null || message != null) {
            recoveryActionService.approveAndDispatch(id, approvedBy, tone, message);
        } else {
            recoveryActionService.approveAndDispatch(id, approvedBy);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch-approve")
    public ResponseEntity<com.recovermandate.dto.BatchApproveResponse> batchApprove(
            @RequestBody(required = false) com.recovermandate.dto.BatchApproveRequest request) {
        com.recovermandate.dto.BatchApproveResponse response = recoveryActionService.batchApprove(
                request != null ? request : new com.recovermandate.dto.BatchApproveRequest()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> rejectAction(@PathVariable Long id, @Valid @RequestBody RejectActionRequest request) {
        recoveryActionService.rejectAction(id, "HUMAN", request.getReason());
        return ResponseEntity.ok().build();
    }

    @org.springframework.web.bind.annotation.GetMapping
    public org.springframework.data.domain.Page<com.recovermandate.dto.RecoveryActionResponse> getRecoveryActions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return recoveryActionService.getRecoveryActions(
                status,
                org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id"))
        );
    }
}
