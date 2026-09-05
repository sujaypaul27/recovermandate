package com.recovermandate.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for normalizing and substituting payment link placeholders across communication channels.
 */
public final class PaymentLinkPlaceholderUtil {

    private PaymentLinkPlaceholderUtil() {}

    // Matches any localhost URL, #pay or #checkout hash URLs, preview URLs, or generic plink preview patterns
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "https?://(?:localhost:\\d+|[a-zA-Z0-9.-]+)/#(?:pay|checkout)/[a-zA-Z0-9_.-]+|" +
            "https?://localhost:\\d+/[^\\s\"'>)]*|" +
            "https?://rzp\\.io/l/preview_[a-zA-Z0-9_.-]+"
    );

    /**
     * Replaces any local/demo/preview placeholder links in the given text with the real target payment link URL.
     *
     * @param text           The original message or draft text
     * @param realPaymentUrl The finalized short payment link URL (e.g. https://rzp.io/... or local demo link)
     * @return The updated text with all placeholder links substituted
     */
    public static String replacePlaceholderLinks(String text, String realPaymentUrl) {
        if (text == null || text.isBlank() || realPaymentUrl == null || realPaymentUrl.isBlank()) {
            return text;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.replaceAll(Matcher.quoteReplacement(realPaymentUrl));
        }

        return text;
    }
}
