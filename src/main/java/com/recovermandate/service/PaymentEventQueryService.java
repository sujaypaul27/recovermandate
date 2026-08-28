package com.recovermandate.service;

import com.recovermandate.dto.PaymentEventResponse;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.repository.FailureClassificationRepository;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentEventQueryService {

    private final PaymentEventRepository paymentEventRepository;
    private final FailureClassificationRepository failureClassificationRepository;
    private final RecoveryActionRepository recoveryActionRepository;

    public Page<PaymentEventResponse> getPaymentEvents(String category, String status, Pageable pageable) {
        Page<PaymentEvent> events = paymentEventRepository.findByFilters(category, status, pageable);
        return events.map(this::mapToResponse);
    }

    private PaymentEventResponse mapToResponse(PaymentEvent event) {
        PaymentEventResponse response = PaymentEventResponse.builder()
                .id(event.getId())
                .traceId(event.getTraceId())
                .razorpayPaymentId(event.getRazorpayPaymentId())
                .eventType(event.getEventType())
                .amount(event.getAmount())
                .receivedAt(event.getReceivedAt())
                .build();

        Optional<FailureClassification> classificationOpt = failureClassificationRepository.findByPaymentEvent(event);
        if (classificationOpt.isPresent()) {
            FailureClassification fc = classificationOpt.get();
            response.setClassificationCategory(fc.getCategory());
            response.setAutoRecoverable(fc.isAutoRecoverable());
            
            Optional<RecoveryAction> actionOpt = recoveryActionRepository.findByFailureClassification(fc);
            if (actionOpt.isPresent()) {
                response.setClassificationStatus(actionOpt.get().getStatus());
            } else if (fc.isAutoRecoverable()) {
                response.setClassificationStatus("AUTO_RECOVERED");
            } else {
                response.setClassificationStatus("PENDING_DRAFT");
            }
        }

        return response;
    }
}
