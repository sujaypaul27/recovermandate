package com.recovermandate.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentLinkPlaceholderUtilTest {

    @Test
    @DisplayName("Should replace localhost preview URL with live Razorpay link")
    void replacePlaceholderLinks_localhostPreviewToLiveRazorpay() {
        String input = "Hello Alice,\n\nYour payment failed. Securely retry:\nhttp://localhost:5173/#pay/plink_preview_act_32\n\nCancel anytime.";
        String liveUrl = "https://rzp.io/rzp/x9Y8z7W6";

        String result = PaymentLinkPlaceholderUtil.replacePlaceholderLinks(input, liveUrl);

        assertEquals("Hello Alice,\n\nYour payment failed. Securely retry:\nhttps://rzp.io/rzp/x9Y8z7W6\n\nCancel anytime.", result);
        assertFalse(result.contains("localhost"));
        assertFalse(result.contains("plink_preview"));
    }

    @Test
    @DisplayName("Should replace localhost simulated URL with live Razorpay link")
    void replacePlaceholderLinks_localhostSimToLiveRazorpay() {
        String input = "Please pay using this link: http://localhost:5173/#pay/plink_sim_123456 thank you.";
        String liveUrl = "https://rzp.io/rzp/realLink123";

        String result = PaymentLinkPlaceholderUtil.replacePlaceholderLinks(input, liveUrl);

        assertEquals("Please pay using this link: https://rzp.io/rzp/realLink123 thank you.", result);
    }

    @Test
    @DisplayName("Should replace rzp.io preview URL with finalized live Razorpay link")
    void replacePlaceholderLinks_rzpPreviewToFinalLink() {
        String input = "Pay here: https://rzp.io/l/preview_act_42 to restore mandate.";
        String liveUrl = "https://rzp.io/rzp/final42";

        String result = PaymentLinkPlaceholderUtil.replacePlaceholderLinks(input, liveUrl);

        assertEquals("Pay here: https://rzp.io/rzp/final42 to restore mandate.", result);
    }

    @Test
    @DisplayName("Should replace localhost preview URL with demo checkout URL in simulated mode")
    void replacePlaceholderLinks_previewToDemoLinkInSimulatedMode() {
        String input = "Retry: http://localhost:5173/#pay/plink_preview_act_99";
        String demoUrl = "http://localhost:5173/#pay/plink_sim_99999";

        String result = PaymentLinkPlaceholderUtil.replacePlaceholderLinks(input, demoUrl);

        assertEquals("Retry: http://localhost:5173/#pay/plink_sim_99999", result);
    }

    @Test
    @DisplayName("Should return original text if text is null or empty or realPaymentUrl is null")
    void replacePlaceholderLinks_nullOrEmpty_safe() {
        assertNull(PaymentLinkPlaceholderUtil.replacePlaceholderLinks(null, "https://rzp.io/l/abc"));
        assertEquals("", PaymentLinkPlaceholderUtil.replacePlaceholderLinks("", "https://rzp.io/l/abc"));
        assertEquals("Hello world", PaymentLinkPlaceholderUtil.replacePlaceholderLinks("Hello world", null));
        assertEquals("Hello world", PaymentLinkPlaceholderUtil.replacePlaceholderLinks("Hello world", ""));
    }
}
