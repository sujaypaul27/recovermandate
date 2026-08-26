package com.recovermandate.webhook;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.service.WebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RazorpayWebhookController.class)
class RazorpayWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RazorpaySignatureVerifier signatureVerifier;

    @MockBean
    private WebhookService webhookService;

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
    @DisplayName("POST /api/webhooks/razorpay returns 400 Bad Request when signature is invalid")
    void handleWebhook_invalidSignature_returns400() throws Exception {
        when(signatureVerifier.verify(eq(SAMPLE_PAYLOAD), eq(INVALID_SIGNATURE))).thenReturn(false);

        mockMvc.perform(post("/api/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", INVALID_SIGNATURE)
                        .content(SAMPLE_PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid signature"));

        verify(webhookService).recordInvalidSignature(SAMPLE_PAYLOAD, INVALID_SIGNATURE);
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
        verify(webhookService, never()).handleVerifiedEvent(anyString());
    }
}
