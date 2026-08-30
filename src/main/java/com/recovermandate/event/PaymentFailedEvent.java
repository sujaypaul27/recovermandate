package com.recovermandate.event;

import com.recovermandate.entity.PaymentEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Internal Spring application event published when a payment.failed webhook is ingested.
 * Decouples immediate webhook HTTP ACK from asynchronous failure classification, retry scheduling,
 * and Gemini AI draft generation.
 */
@Getter
public class PaymentFailedEvent extends ApplicationEvent {

    private final PaymentEvent paymentEvent;

    public PaymentFailedEvent(Object source, PaymentEvent paymentEvent) {
        super(source);
        this.paymentEvent = paymentEvent;
    }
}
