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
    private final com.recovermandate.repository.PaymentLinkRepository paymentLinkRepository;
    private final com.recovermandate.repository.RetryScheduleRepository retryScheduleRepository;

    public Page<PaymentEventResponse> getPaymentEvents(String category, String status, Pageable pageable) {
        Page<PaymentEvent> events = paymentEventRepository.findByFilters(category, status, pageable);
        return events.map(this::mapToResponse);
    }

    private PaymentEventResponse mapToResponse(PaymentEvent event) {
        String customerName = null;
        String customerEmail = null;
        String subscriptionId = null;
        String planName = null;

        if (event.getSubscription() != null) {
            subscriptionId = event.getSubscription().getRazorpaySubscriptionId();
            if (event.getSubscription().getCustomer() != null) {
                customerName = event.getSubscription().getCustomer().getName();
                customerEmail = event.getSubscription().getCustomer().getEmail();
            }
            if (event.getSubscription().getPlan() != null) {
                planName = event.getSubscription().getPlan().getRazorpayPlanId();
            }
        }

        PaymentEventResponse response = PaymentEventResponse.builder()
                .id(event.getId())
                .traceId(event.getTraceId())
                .razorpayPaymentId(event.getRazorpayPaymentId())
                .eventType(event.getEventType())
                .amount(event.getAmount())
                .receivedAt(event.getReceivedAt())
                .failureReasonCode(event.getFailureReasonCode())
                .customerName(customerName)
                .customerEmail(customerEmail)
                .subscriptionId(subscriptionId)
                .planName(planName)
                .build();

        Optional<FailureClassification> classificationOpt = failureClassificationRepository.findByPaymentEvent(event);
        if (classificationOpt.isPresent()) {
            FailureClassification fc = classificationOpt.get();
            response.setClassificationCategory(fc.getCategory());
            response.setAutoRecoverable(fc.isAutoRecoverable());
            
            Optional<RecoveryAction> actionOpt = recoveryActionRepository.findByFailureClassification(fc);
            if (actionOpt.isPresent()) {
                RecoveryAction action = actionOpt.get();
                response.setClassificationStatus(action.getStatus());
                response.setRecoveryActionId(action.getId());
                response.setPaymentLinkUrl(action.getPaymentLinkUrl());

                if (paymentLinkRepository != null) {
                    paymentLinkRepository.findByRecoveryAction(action).ifPresent(pl -> {
                        response.setPaymentLinkId(pl.getRazorpayLinkId());
                        response.setPaymentLinkUrl(pl.getShortUrl());
                    });
                }
            } else if (fc.isAutoRecoverable()) {
                response.setClassificationStatus("AUTO_RECOVERED");
            } else {
                response.setClassificationStatus("PENDING_DRAFT");
            }
        }

        // Map associated retry schedules
        if (event.getId() != null && retryScheduleRepository != null) {
            java.util.List<com.recovermandate.entity.RetrySchedule> schedules =
                    retryScheduleRepository.findByPaymentEventIdOrderByAttemptNumberAsc(event.getId());
            if (schedules != null && !schedules.isEmpty()) {
                java.util.List<com.recovermandate.dto.RetryScheduleDto> dtos = schedules.stream()
                        .map(s -> com.recovermandate.dto.RetryScheduleDto.builder()
                                .id(s.getId())
                                .attemptNumber(s.getAttemptNumber())
                                .scheduledAt(s.getScheduledAt())
                                .executedAt(s.getExecutedAt())
                                .status(s.getResult())
                                .scheduleReason(s.getScheduleReason())
                                .failureCategory(s.getFailureCategory())
                                .razorpayRetryPaymentId(s.getRazorpayRetryPaymentId())
                                .build())
                        .toList();
                response.setRetrySchedules(dtos);
            } else {
                response.setRetrySchedules(java.util.Collections.emptyList());
            }
        } else {
            response.setRetrySchedules(java.util.Collections.emptyList());
        }

        return response;
    }
}
