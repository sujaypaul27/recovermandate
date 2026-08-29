package com.recovermandate.ai;

/**
 * Record representing the generated recovery draft message and its origination source (e.g. "AI" or "HEURISTIC").
 */
public record DraftResult(String message, String source) {
}
