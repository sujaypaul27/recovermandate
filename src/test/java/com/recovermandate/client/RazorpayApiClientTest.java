package com.recovermandate.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RazorpayApiClientTest {

    @Test
    @DisplayName("Should generate simulated demo checkout link when credentials are blank")
    void createPaymentLink_simulatedModeWhenKeysBlank() {
        RestTemplateBuilder builder = new RestTemplateBuilder();
        ObjectMapper mapper = new ObjectMapper();
        RazorpayApiClient client = new RazorpayApiClient(builder, mapper);

        ReflectionTestUtils.setField(client, "keyId", "");
        ReflectionTestUtils.setField(client, "keySecret", "");
        ReflectionTestUtils.setField(client, "appUrl", "http://localhost:5173");

        assertFalse(client.isLiveMode());

        Map<String, String> link = client.createPaymentLink(
                49900L,
                "INR",
                "subscriber@example.com",
                "John Doe",
                "Mandate Recovery",
                Instant.now().plusSeconds(86400),
                "rec_act_123"
        );

        assertNotNull(link);
        assertTrue(link.containsKey("id"));
        assertTrue(link.containsKey("short_url"));
        assertTrue(link.get("id").startsWith("plink_sim_"));
        assertTrue(link.get("short_url").contains("/#/pay/plink_sim_"));
    }

    @Test
    @DisplayName("Should call Razorpay API and return genuine rzp.io short_url when credentials are configured")
    void createPaymentLink_liveModeCallsRazorpayApi() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any())).thenReturn(builder);
        when(builder.setReadTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);

        ObjectMapper mapper = new ObjectMapper();
        RazorpayApiClient client = new RazorpayApiClient(builder, mapper);

        ReflectionTestUtils.setField(client, "keyId", "rzp_live_mockKey123");
        ReflectionTestUtils.setField(client, "keySecret", "mockSecret456");
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.razorpay.com/v1");

        assertTrue(client.isLiveMode());

        String razorpayApiResponse = """
                {
                  "id": "plink_live_rzp_999",
                  "short_url": "https://rzp.io/l/liveCheckout999",
                  "status": "created",
                  "amount": 49900
                }
                """;

        when(restTemplate.postForEntity(
                eq("https://api.razorpay.com/v1/payment_links"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(razorpayApiResponse));

        Map<String, String> link = client.createPaymentLink(
                49900L,
                "INR",
                "subscriber@realdomain.com",
                "Jane Doe",
                "Mandate Recovery",
                Instant.now().plusSeconds(86400),
                "rec_act_999"
        );

        assertNotNull(link);
        assertEquals("plink_live_rzp_999", link.get("id"));
        assertEquals("https://rzp.io/l/liveCheckout999", link.get("short_url"));
        verify(restTemplate).postForEntity(eq("https://api.razorpay.com/v1/payment_links"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("Should auto-free quota and retry when Razorpay returns 429 quota reached")
    void createPaymentLink_autoFreesQuotaAndRetriesOn429() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any())).thenReturn(builder);
        when(builder.setReadTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);

        ObjectMapper mapper = new ObjectMapper();
        RazorpayApiClient client = new RazorpayApiClient(builder, mapper);

        ReflectionTestUtils.setField(client, "keyId", "rzp_test_mockKey123");
        ReflectionTestUtils.setField(client, "keySecret", "mockSecret456");
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.razorpay.com/v1");

        String error429 = "{\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\",\"description\":\"test mode limit of 30 reached for payment_link\"}}";
        org.springframework.web.client.HttpClientErrorException rateLimitException =
                new org.springframework.web.client.HttpClientErrorException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests",
                        error429.getBytes(),
                        java.nio.charset.StandardCharsets.UTF_8
                );

        String successResponse = """
                {
                  "id": "plink_retry_success",
                  "short_url": "https://rzp.io/l/retryCheckout",
                  "status": "created"
                }
                """;

        String listLinksResponse = """
                {
                  "payment_links": [
                    {"id": "plink_old_1", "status": "created"},
                    {"id": "plink_old_2", "status": "created"}
                  ]
                }
                """;

        when(restTemplate.postForEntity(eq("https://api.razorpay.com/v1/payment_links"), any(HttpEntity.class), eq(String.class)))
                .thenThrow(rateLimitException)
                .thenReturn(ResponseEntity.ok(successResponse));

        when(restTemplate.exchange(eq("https://api.razorpay.com/v1/payment_links?count=20"), eq(org.springframework.http.HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(listLinksResponse));

        when(restTemplate.postForEntity(contains("/cancel"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"status\":\"cancelled\"}"));

        Map<String, String> link = client.createPaymentLink(
                49900L,
                "INR",
                "subscriber@realdomain.com",
                "Jane Doe",
                "Mandate Recovery",
                Instant.now().plusSeconds(86400),
                "rec_act_999"
        );

        assertNotNull(link);
        assertEquals("plink_retry_success", link.get("id"));
        assertEquals("https://rzp.io/l/retryCheckout", link.get("short_url"));
    }

    @Test
    @DisplayName("Should fall back to local demo link pattern when API fails in test mode")
    void createPaymentLink_fallbackToLocalDemoLinkWhenApiFailsInTestMode() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any())).thenReturn(builder);
        when(builder.setReadTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);

        ObjectMapper mapper = new ObjectMapper();
        RazorpayApiClient client = new RazorpayApiClient(builder, mapper);

        ReflectionTestUtils.setField(client, "keyId", "rzp_test_mockKey123");
        ReflectionTestUtils.setField(client, "keySecret", "mockSecret456");
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.razorpay.com/v1");
        ReflectionTestUtils.setField(client, "appUrl", "http://localhost:5173");

        org.springframework.web.client.HttpClientErrorException badRequestException =
                new org.springframework.web.client.HttpClientErrorException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Bad Request"
                );

        when(restTemplate.postForEntity(eq("https://api.razorpay.com/v1/payment_links"), any(HttpEntity.class), eq(String.class)))
                .thenThrow(badRequestException);

        Map<String, String> link = client.createPaymentLink(
                49900L,
                "INR",
                "subscriber@realdomain.com",
                "Jane Doe",
                "Mandate Recovery",
                Instant.now().plusSeconds(86400),
                "rec_act_999"
        );

        assertNotNull(link);
        assertTrue(link.get("id").startsWith("plink_quota_"));
        assertTrue(link.get("short_url").startsWith("http://localhost:5173/#/pay/plink_quota_"));
    }

    @Test
    @DisplayName("Should strictly throw RuntimeException when API fails in Live Mode (rzp_live_...)")
    void createPaymentLink_liveModeThrowsOnApiFailure() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any())).thenReturn(builder);
        when(builder.setReadTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);

        ObjectMapper mapper = new ObjectMapper();
        RazorpayApiClient client = new RazorpayApiClient(builder, mapper);

        ReflectionTestUtils.setField(client, "keyId", "rzp_live_productionKey123");
        ReflectionTestUtils.setField(client, "keySecret", "prodSecret456");
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.razorpay.com/v1");

        assertTrue(client.isLiveMode());

        org.springframework.web.client.HttpClientErrorException badRequestException =
                new org.springframework.web.client.HttpClientErrorException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Bad Request"
                );

        when(restTemplate.postForEntity(eq("https://api.razorpay.com/v1/payment_links"), any(HttpEntity.class), eq(String.class)))
                .thenThrow(badRequestException);

        assertThrows(RuntimeException.class, () -> client.createPaymentLink(
                49900L,
                "INR",
                "subscriber@realdomain.com",
                "Jane Doe",
                "Mandate Recovery",
                Instant.now().plusSeconds(86400),
                "rec_act_999"
        ));
    }
}
