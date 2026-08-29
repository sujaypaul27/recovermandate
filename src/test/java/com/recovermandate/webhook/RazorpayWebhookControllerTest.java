package com.recovermandate.webhook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.WebhookDlq;
import com.recovermandate.repository.WebhookDlqRepository;
import com.recovermandate.service.WebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(RazorpayWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class RazorpayWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RazorpaySignatureVerifier signatureVerifier;

    @MockBean
    private WebhookService webhookService;

    @MockBean
    private WebhookDlqRepository webhookDlqRepository;

    private static final String SAMPLE_PAYLOAD = "{\"event\":\"payment.failed\"}";
    private static final String VALID_SIGNATURE = "valid_hex_signature";
    private static final String INVALID_SIGNATURE = "invalid_hex_signature";

    @Test
    @DisplayName("POST /api/webhooks/razorpay returns 200 OK when signature is valid")
    void handleWebhook_validSignature_returns200() throws Exception {
        when(signatureVerifier.verify(eq(SAMPLE_PAYLOAD), eq(VALID_SIGNATURE))).thenReturn(true);
        when(webhookService.handleVerifiedEvent(SAMPLE_PAYLOAD))
                .thenReturn(PaymentEvent.builder().id(101L).build());

        mockMvc.perform(post("/api/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", VALID_SIGNATURE)
                        .content(SAMPLE_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("101")));

        verify(webhookService).handleVerifiedEvent(SAMPLE_PAYLOAD);
    }

    @Test
    @DisplayName("POST /api/webhooks/razorpay returns 400 Bad Request and persists to DLQ when signature is invalid")
    void handleWebhook_invalidSignature_returns400AndPersistsDlq() throws Exception {
        when(signatureVerifier.verify(eq(SAMPLE_PAYLOAD), eq(INVALID_SIGNATURE))).thenReturn(false);

        mockMvc.perform(post("/api/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", INVALID_SIGNATURE)
                        .content(SAMPLE_PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid signature"));

        verify(webhookService).recordInvalidSignature(SAMPLE_PAYLOAD, INVALID_SIGNATURE);
        verify(webhookDlqRepository).save(any(WebhookDlq.class));
        verify(webhookService, never()).handleVerifiedEvent(anyString());
    }

    @Test
    @DisplayName("POST /api/webhooks/razorpay returns 400 Bad Request when signature header is missing")
    void handleWebhook_missingSignatureHeader_returns400() throws Exception {
        when(signatureVerifier.verify(eq(SAMPLE_PAYLOAD), eq(null))).thenReturn(false);

        mockMvc.perform(post("/api/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE_PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid signature"));

        verify(webhookService).recordInvalidSignature(SAMPLE_PAYLOAD, null);
        verify(webhookDlqRepository).save(any(WebhookDlq.class));
        verify(webhookService, never()).handleVerifiedEvent(anyString());
    }

    @Test
    @DisplayName("GET /api/webhooks/dlq returns list of rejected webhooks")
    void getDlqEvents_returnsList() throws Exception {
        WebhookDlq dlq = WebhookDlq.builder()
                .id(1L)
                .payload("{\"event\":\"test\"}")
                .errorMessage("Invalid signature")
                .status("REJECTED")
                .createdAt(Instant.now())
                .build();

        when(webhookDlqRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(dlq));

        mockMvc.perform(get("/api/webhooks/dlq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].errorMessage").value("Invalid signature"));
    }

    @Test
    @DisplayName("POST /api/webhooks/dlq/{id}/replay replays payload and updates status to REPLAYED")
    void replayDlqEvent_success() throws Exception {
        WebhookDlq dlq = WebhookDlq.builder()
                .id(5L)
                .payload(SAMPLE_PAYLOAD)
                .status("REJECTED")
                .createdAt(Instant.now())
                .build();

        PaymentEvent event = PaymentEvent.builder().id(555L).build();

        when(webhookDlqRepository.findById(5L)).thenReturn(Optional.of(dlq));
        when(webhookService.handleVerifiedEvent(SAMPLE_PAYLOAD)).thenReturn(event);

        mockMvc.perform(post("/api/webhooks/dlq/5/replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPLAYED"))
                .andExpect(jsonPath("$.dlqId").value(5))
                .andExpect(jsonPath("$.eventId").value(555));

        verify(webhookService).handleVerifiedEvent(SAMPLE_PAYLOAD);
        verify(webhookDlqRepository).save(dlq);
    }
}
