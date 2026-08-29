package com.recovermandate.controller;

import com.recovermandate.dto.RejectActionRequest;
import com.recovermandate.service.RecoveryActionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recovery-actions")
@RequiredArgsConstructor
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
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        return recoveryActionService.getRecoveryActions(status, org.springframework.data.domain.PageRequest.of(page, size));
    }
}
