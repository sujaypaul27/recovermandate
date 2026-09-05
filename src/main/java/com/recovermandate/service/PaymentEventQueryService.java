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
    private final com.recovermandate.client.RazorpayApiClient razorpayApiClient;

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

        if ((customerEmail == null || customerEmail.isBlank() || WebhookService.isPlaceholderOrVoidEmail(customerEmail)) && event.getRawPayload() != null && !event.getRawPayload().isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(event.getRawPayload());
                com.fasterxml.jackson.databind.JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                com.fasterxml.jackson.databind.JsonNode subscriptionEntity = root.path("payload").path("subscription").path("entity");
                String rawEmail = WebhookService.extractCustomerEmail(root, paymentEntity, subscriptionEntity);
                if (rawEmail != null && !WebhookService.isPlaceholderOrVoidEmail(rawEmail)) {
                    customerEmail = rawEmail;
                    customerName = WebhookService.extractCustomerName(root, paymentEntity, subscriptionEntity, customerEmail);
                }
            } catch (Exception ignored) {
            }
        }

        if (WebhookService.isPlaceholderOrVoidEmail(customerEmail)) {
            customerEmail = "sujaypaul2711@gmail.com";
            if (customerName == null || customerName.isBlank() || "Void".equalsIgnoreCase(customerName)) {
                customerName = "Sujay Paul";
            }
        }

        boolean isDemo = Boolean.TRUE.equals(event.isDemoData())
                || (event.getRazorpayPaymentId() != null && event.getRazorpayPaymentId().startsWith("pay_demo_"))
                || (customerEmail != null && (customerEmail.contains("demo.customer") || customerEmail.contains("sujaypaul2711@gmail.com")));

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
                .isDemoData(isDemo)
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

                        // Real-time synchronization check with Razorpay API for live payment links
                        if (!"RECOVERED".equalsIgnoreCase(action.getStatus()) && !"PAID".equalsIgnoreCase(pl.getStatus()) && razorpayApiClient != null) {
                            try {
                                com.fasterxml.jackson.databind.JsonNode rzpLink = razorpayApiClient.fetchPaymentLink(pl.getRazorpayLinkId());
                                if (rzpLink != null) {
                                    String rzpStatus = rzpLink.path("status").asText();
                                    long amountPaid = rzpLink.path("amount_paid").asLong(0);
                                    if ("paid".equalsIgnoreCase(rzpStatus) || amountPaid > 0) {
                                        pl.setStatus("PAID");
                                        pl.setPaidAt(java.time.Instant.now());
                                        paymentLinkRepository.save(pl);

                                        action.setStatus("RECOVERED");
                                        recoveryActionRepository.save(action);
                                        response.setClassificationStatus("RECOVERED");

                                        if (event.getId() != null && retryScheduleRepository != null) {
                                            java.util.List<com.recovermandate.entity.RetrySchedule> pendingRetries =
                                                    retryScheduleRepository.findByPaymentEventIdAndResult(event.getId(), "PENDING");
                                            for (com.recovermandate.entity.RetrySchedule pendingRetry : pendingRetries) {
                                                pendingRetry.setResult("SKIPPED");
                                                pendingRetry.setExecutedAt(java.time.Instant.now());
                                                pendingRetry.setScheduleReason("SUPERSEDED_BY_LINK_PAYMENT");
                                                retryScheduleRepository.save(pendingRetry);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }

                        if ("PAID".equalsIgnoreCase(pl.getStatus())) {
                            response.setClassificationStatus("RECOVERED");
                        }
                    });
                }
            } else if (fc.isAutoRecoverable()) {
                response.setClassificationStatus("AUTO_RECOVERED");
            } else {
                response.setClassificationStatus("PENDING_DRAFT");
            }
        }

        // Closed-loop check: if this event is not recovered, check if its parent subscription or payment link was recovered
        if (!"RECOVERED".equalsIgnoreCase(response.getClassificationStatus())
                && !"PAID".equalsIgnoreCase(response.getClassificationStatus())
                && !"AUTO_RECOVERED".equalsIgnoreCase(response.getClassificationStatus())) {
            if (event.getSubscription() != null && event.getSubscription().getId() != null) {
                try {
                    java.util.List<com.recovermandate.entity.RecoveryAction> siblingActions =
                            recoveryActionRepository.findBySubscriptionId(event.getSubscription().getId());
                    boolean hasRecoveredSibling = siblingActions.stream()
                            .anyMatch(ra -> "RECOVERED".equalsIgnoreCase(ra.getStatus()));
                    if (hasRecoveredSibling) {
                        response.setClassificationStatus("COMPLETED");
                    }
                } catch (Exception ignored) {
                }
            }
        }

        boolean isRecovered = "RECOVERED".equalsIgnoreCase(response.getClassificationStatus())
                || "PAID".equalsIgnoreCase(response.getClassificationStatus())
                || "AUTO_RECOVERED".equalsIgnoreCase(response.getClassificationStatus())
                || "subscription.charged".equalsIgnoreCase(event.getEventType())
                || "payment_link.paid".equalsIgnoreCase(event.getEventType());

        boolean isCompleted = "COMPLETED".equalsIgnoreCase(response.getClassificationStatus())
                || "SUPERSEDED".equalsIgnoreCase(response.getClassificationStatus())
                || "SUPERSEDED_BY_LINK_PAYMENT".equalsIgnoreCase(response.getClassificationStatus());

        if (isRecovered) {
            response.setRecoveryStatus("RECOVERED");
        } else if (isCompleted) {
            response.setRecoveryStatus("COMPLETED");
        } else {
            response.setRecoveryStatus("IN_PROGRESS");
        }

        // Map associated retry schedules (deduplicated by attempt number)
        if (event.getId() != null && retryScheduleRepository != null) {
            java.util.List<com.recovermandate.entity.RetrySchedule> schedules =
                    retryScheduleRepository.findByPaymentEventIdOrderByAttemptNumberAsc(event.getId());
            if (schedules != null && !schedules.isEmpty()) {
                java.util.Map<Integer, com.recovermandate.dto.RetryScheduleDto> uniqueByAttempt = new java.util.LinkedHashMap<>();
                for (com.recovermandate.entity.RetrySchedule s : schedules) {
                    if (!uniqueByAttempt.containsKey(s.getAttemptNumber())) {
                        uniqueByAttempt.put(s.getAttemptNumber(), com.recovermandate.dto.RetryScheduleDto.builder()
                                .id(s.getId())
                                .attemptNumber(s.getAttemptNumber())
                                .scheduledAt(s.getScheduledAt())
                                .executedAt(s.getExecutedAt())
                                .status(s.getResult())
                                .scheduleReason(s.getScheduleReason())
                                .failureCategory(s.getFailureCategory())
                                .razorpayRetryPaymentId(s.getRazorpayRetryPaymentId())
                                .build());
                    }
                }
                response.setRetrySchedules(new java.util.ArrayList<>(uniqueByAttempt.values()));
            } else {
                response.setRetrySchedules(java.util.Collections.emptyList());
            }
        } else {
            response.setRetrySchedules(java.util.Collections.emptyList());
        }

        return response;
    }
}
