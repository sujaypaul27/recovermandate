package com.recovermandate.webhook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RazorpaySignatureVerifierTest {

    private static final String TEST_SECRET = "top_secret_key_12345";
    private static final String SAMPLE_PAYLOAD = "{\"entity\":\"event\",\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_test123\",\"amount\":50000}}}}";

    private RazorpaySignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new RazorpaySignatureVerifier(TEST_SECRET);
    }

    @Test
    @DisplayName("Should accept valid HMAC-SHA256 signature")
    void shouldAcceptValidSignature() throws Exception {
        String expectedSignature = verifier.calculateHmacSha256(SAMPLE_PAYLOAD, TEST_SECRET);
        assertNotNull(expectedSignature);

        boolean isValid = verifier.verify(SAMPLE_PAYLOAD, expectedSignature);
        assertTrue(isValid, "Valid signature must be accepted");
    }

    @Test
    @DisplayName("Should accept valid signature with uppercase/mixed case input (case insensitive comparison)")
    void shouldAcceptValidSignatureCaseInsensitive() throws Exception {
        String expectedSignature = verifier.calculateHmacSha256(SAMPLE_PAYLOAD, TEST_SECRET);
        String upperSignature = expectedSignature.toUpperCase();

        boolean isValid = verifier.verify(SAMPLE_PAYLOAD, upperSignature);
        assertTrue(isValid, "Hex comparison should handle case differences");
    }

    @Test
    @DisplayName("Should accept valid signature with leading/trailing whitespace")
    void shouldAcceptValidSignatureWithWhitespace() throws Exception {
        String expectedSignature = verifier.calculateHmacSha256(SAMPLE_PAYLOAD, TEST_SECRET);
        String paddedSignature = "   " + expectedSignature + "  \n";

        boolean isValid = verifier.verify(SAMPLE_PAYLOAD, paddedSignature);
        assertTrue(isValid, "Signature with trimmed whitespace should be valid");
    }

    @Test
    @DisplayName("Should reject invalid signature")
    void shouldRejectInvalidSignature() {
        String invalidSignature = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

        boolean isValid = verifier.verify(SAMPLE_PAYLOAD, invalidSignature);
        assertFalse(isValid, "Invalid signature must be rejected");
    }

    @Test
    @DisplayName("Should reject tampered payload even with originally valid signature")
    void shouldRejectTamperedPayload() throws Exception {
        String originalSignature = verifier.calculateHmacSha256(SAMPLE_PAYLOAD, TEST_SECRET);
        String tamperedPayload = SAMPLE_PAYLOAD.replace("50000", "99999");

        boolean isValid = verifier.verify(tamperedPayload, originalSignature);
        assertFalse(isValid, "Tampered payload must be rejected");
    }

    @Test
    @DisplayName("Should reject signature generated with wrong secret key")
    void shouldRejectSignatureWithWrongSecret() throws Exception {
        String wrongSecret = "different_secret_key";
        String signatureWithWrongSecret = verifier.calculateHmacSha256(SAMPLE_PAYLOAD, wrongSecret);

        boolean isValid = verifier.verify(SAMPLE_PAYLOAD, signatureWithWrongSecret);
        assertFalse(isValid, "Signature computed with different secret must be rejected");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Should reject null, empty, or blank signatures")
    void shouldRejectNullOrBlankSignature(String signature) {
        boolean isValid = verifier.verify(SAMPLE_PAYLOAD, signature);
        assertFalse(isValid, "Null or blank signature must be rejected");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should reject null or empty payload")
    void shouldRejectNullOrEmptyPayload(String payload) {
        boolean isValid = verifier.verify(payload, "some_signature");
        assertFalse(isValid, "Null or empty payload must be rejected");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("Should reject when secret is not configured or blank")
    void shouldRejectWhenSecretIsBlank(String blankSecret) {
        RazorpaySignatureVerifier unconfiguredVerifier = new RazorpaySignatureVerifier(blankSecret);
        boolean isValid = unconfiguredVerifier.verify(SAMPLE_PAYLOAD, "some_signature");
        assertFalse(isValid, "Verification must fail when secret is blank or missing");
    }
}
