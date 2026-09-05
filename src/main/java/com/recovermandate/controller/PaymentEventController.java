package com.recovermandate.controller;

import com.recovermandate.dto.PaymentEventResponse;
import com.recovermandate.service.PaymentEventQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller providing paginated query access to captured webhook payment events,
 * classifications, and recovery progress.
 */
@RestController
@RequestMapping("/api/payment-events")
@RequiredArgsConstructor
@Validated
public class PaymentEventController {

    private final PaymentEventQueryService paymentEventQueryService;

    @GetMapping
    public Page<PaymentEventResponse> getPaymentEvents(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        
        return paymentEventQueryService.getPaymentEvents(
                category,
                status,
                PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "receivedAt", "id"))
        );
    }
}
