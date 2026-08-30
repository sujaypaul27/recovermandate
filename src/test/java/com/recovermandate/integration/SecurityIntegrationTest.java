package com.recovermandate.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenMissingApiKey_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenInvalidApiKey_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                .header("X-API-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenValidApiKey_thenSuccess() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                .header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk());
    }

    @Test
    void whenWebhookEndpoint_thenPermitAllAuth() throws Exception {
        // Just expect 400 because payload is empty/missing HMAC, but NOT 401 Unauthorized
        mockMvc.perform(post("/api/webhooks/razorpay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenCheckoutEndpoint_withoutApiKey_thenPermitAllAuth() throws Exception {
        // Unauthenticated customer access to checkout should NOT return 401 Unauthorized (SEC-03)
        // Returns 404 since plink_nonexistent does not exist, confirming auth was bypassed
        mockMvc.perform(get("/api/checkout/plink_nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rateLimiterTripsAfter50Requests() throws Exception {
        // MockMvc uses 127.0.0.1 as default remote address.
        // The bucket allows 50 requests per minute for this IP.
        
        // Let's use an isolated IP so it doesn't conflict with other tests
        String isolatedIp = "192.168.1.100";

        // Send 50 valid requests
        for (int i = 0; i < 50; i++) {
            mockMvc.perform(post("/api/recovery-actions/999/approve")
                    .header("X-API-Key", "test-api-key")
                    .header("X-Forwarded-For", isolatedIp))
                    // Expect 404 since 999 doesn't exist, which means it passed rate limiting
                    .andExpect(status().isNotFound());
        }

        // The 51st request should be rate-limited (429)
        mockMvc.perform(post("/api/recovery-actions/999/approve")
                .header("X-API-Key", "test-api-key")
                .header("X-Forwarded-For", isolatedIp))
                .andExpect(status().isTooManyRequests());
    }
}
