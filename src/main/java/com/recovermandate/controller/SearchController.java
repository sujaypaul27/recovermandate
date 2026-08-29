package com.recovermandate.controller;

import com.recovermandate.dto.SearchResultItem;
import com.recovermandate.entity.AuditLog;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.repository.AuditLogRepository;
import com.recovermandate.repository.PaymentEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final PaymentEventRepository paymentEventRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<List<SearchResultItem>> search(@RequestParam(name = "q", defaultValue = "") String query) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.ok(List.of());
        }

        String sanitizedQuery = query.trim();
        List<SearchResultItem> results = new ArrayList<>();

        // Search payment events
        List<PaymentEvent> events = paymentEventRepository.searchEvents(sanitizedQuery, PageRequest.of(0, 5));
        for (PaymentEvent pe : events) {
            String amountStr = pe.getAmount() != null ? "₹" + String.format("%.2f", pe.getAmount() / 100.0) : "";
            results.add(SearchResultItem.builder()
                    .id(String.valueOf(pe.getId()))
                    .type("PAYMENT_EVENT")
                    .title(pe.getRazorpayPaymentId())
                    .subtitle(pe.getEventType() + (amountStr.isEmpty() ? "" : " · " + amountStr))
                    .timestamp(pe.getReceivedAt() != null ? pe.getReceivedAt().toString() : "")
                    .build());
        }

        // Search audit logs
        List<AuditLog> logs = auditLogRepository.searchAuditLogs(sanitizedQuery, PageRequest.of(0, 5));
        for (AuditLog log : logs) {
            results.add(SearchResultItem.builder()
                    .id(String.valueOf(log.getId()))
                    .type("AUDIT_LOG")
                    .title(log.getAction() + " (" + log.getEntityType() + " #" + log.getEntityId() + ")")
                    .subtitle(log.getReasoning())
                    .timestamp(log.getCreatedAt() != null ? log.getCreatedAt().toString() : "")
                    .build());
        }

        return ResponseEntity.ok(results);
    }
}
