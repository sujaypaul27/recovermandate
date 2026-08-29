package com.recovermandate.heuristic;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Deterministic Indian Banking Rail Retry Window Calculator.
 * <p>
 * NOTE: This is a domain-specific, rules-based heuristic model (NOT Machine Learning).
 * It calculates optimal retry windows by aligning with known Indian banking clearing cycles,
 * NPCI UPI traffic patterns, and core banking solution (CBS) maintenance windows.
 */
public final class RecoveryWindowCalculator {

    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    // Indian Banking Window Constants (IST)
    public static final LocalTime CBS_START = LocalTime.of(23, 30); // 11:30 PM
    public static final LocalTime CBS_END = LocalTime.of(3, 30);   // 03:30 AM
    public static final LocalTime MORNING_SETTLEMENT_WINDOW = LocalTime.of(10, 0); // 10:00 AM
    public static final LocalTime AFTERNOON_SETTLEMENT_WINDOW = LocalTime.of(14, 0); // 2:00 PM
    public static final LocalTime UPI_PEAK_START = LocalTime.of(19, 0); // 7:00 PM
    public static final LocalTime UPI_PEAK_END = LocalTime.of(21, 30);  // 9:30 PM

    public record SuggestedRetryWindow(Instant scheduledAt, String reason) {
    }

    private RecoveryWindowCalculator() {
        // Private constructor for utility class
    }

    /**
     * Calculates the optimal retry Instant and explainable reason based on Indian banking heuristics.
     *
     * @param failureTime   the Instant the mandate transaction failed (defaults to Instant.now() if null)
     * @param category      the failure classification category (e.g., "insufficient_funds", "technical_decline")
     * @param attemptNumber the retry attempt index (1-based, e.g., 1, 2, 3)
     * @return SuggestedRetryWindow containing scheduledAt Instant and human-readable reason string
     */
    public static SuggestedRetryWindow calculateOptimalWindow(Instant failureTime, String category, int attemptNumber) {
        Instant baseTime = failureTime != null ? failureTime : Instant.now();
        ZonedDateTime istTime = baseTime.atZone(IST_ZONE);
        LocalTime time = istTime.toLocalTime();
        LocalDate date = istTime.toLocalDate();
        int attempt = Math.max(1, attemptNumber);
        String safeCategory = category != null ? category.toLowerCase().trim() : "unknown";

        // -------------------------------------------------------------------------
        // RULE 1: PSU Bank CBS Maintenance Window (11:30 PM – 3:30 AM IST)
        // PSU banks (SBI, PNB, BoB, Canara) perform end-of-day batch postings & db reconciliations.
        // Immediate retries during this window result in high false-positive technical declines.
        // -------------------------------------------------------------------------
        if (isWithinCbsMaintenanceWindow(time)) {
            LocalDate targetDate = (time.isAfter(CBS_START) || time.equals(CBS_START))
                    ? date.plusDays(1)
                    : date;
            
            // Advance further for subsequent retry attempts
            if (attempt > 1) {
                targetDate = targetDate.plusDays((long) (attempt - 1) * 2);
            }

            ZonedDateTime scheduled = targetDate.atTime(MORNING_SETTLEMENT_WINDOW).atZone(IST_ZONE);
            return new SuggestedRetryWindow(
                    scheduled.toInstant(),
                    "Avoiding PSU bank CBS batch maintenance window (11:30 PM–3:30 AM IST) — scheduled for 10:00 AM post-clearing window"
            );
        }

        // -------------------------------------------------------------------------
        // RULE 2: Salary Credit & Liquidity Window for Insufficient Funds
        // In India, corporate and PSU payroll runs on the 1st–3rd or last 2 working days.
        // Retrying immediately on the same dry day is wasteful; schedule for next morning (10:00 AM)
        // when NEFT/NACH salary credits are posted.
        // -------------------------------------------------------------------------
        if (safeCategory.contains("insufficient") && isNearSalaryCreditPeriod(date)) {
            LocalDate targetDate = date.plusDays(1);
            if (attempt > 1) {
                targetDate = targetDate.plusDays((long) (attempt - 1) * 2);
            }

            ZonedDateTime scheduled = targetDate.atTime(MORNING_SETTLEMENT_WINDOW).atZone(IST_ZONE);
            return new SuggestedRetryWindow(
                    scheduled.toInstant(),
                    "Optimized for Indian salary credit liquidity window (1st–3rd / month-end) — scheduled for 10:00 AM next morning"
            );
        }

        // -------------------------------------------------------------------------
        // RULE 3: Peak UPI Evening Traffic Window (7:00 PM – 9:30 PM IST)
        // Retail UPI & mandate transaction traffic surges during 19:00–21:30 IST, causing
        // gateway timeouts. Shift retries to the next morning liquidity window.
        // -------------------------------------------------------------------------
        if (isWithinUpiPeakWindow(time)) {
            LocalDate targetDate = date.plusDays(1);
            if (attempt > 1) {
                targetDate = targetDate.plusDays((long) (attempt - 1) * 2);
            }

            ZonedDateTime scheduled = targetDate.atTime(MORNING_SETTLEMENT_WINDOW).atZone(IST_ZONE);
            return new SuggestedRetryWindow(
                    scheduled.toInstant(),
                    "Avoiding peak UPI evening traffic congestion window (7:00 PM–9:30 PM IST) — shifted to 10:00 AM liquidity window"
            );
        }

        // -------------------------------------------------------------------------
        // RULE 4: Category-Specific & Business Hours Optimization
        // -------------------------------------------------------------------------
        if (safeCategory.contains("technical")) {
            // For technical declines during business hours (09:00–17:00):
            // Attempt 1: Next optimal window (+30 min or 2:00 PM)
            // Attempt 2: 2 hours later or next morning 10:00 AM
            // Attempt 3: Next day 10:00 AM
            if (attempt == 1) {
                if (time.isBefore(LocalTime.of(13, 30))) {
                    // Early morning / midday -> schedule for 2:00 PM afternoon liquidity window
                    ZonedDateTime scheduled = date.atTime(AFTERNOON_SETTLEMENT_WINDOW).atZone(IST_ZONE);
                    if (scheduled.toInstant().isAfter(baseTime.plus(Duration.ofMinutes(15)))) {
                        return new SuggestedRetryWindow(
                                scheduled.toInstant(),
                                "Scheduled within optimal Indian banking afternoon settlement window (2:00 PM IST)"
                        );
                    }
                }
                // Later in afternoon -> next morning 10:00 AM
                ZonedDateTime scheduled = date.plusDays(1).atTime(MORNING_SETTLEMENT_WINDOW).atZone(IST_ZONE);
                return new SuggestedRetryWindow(
                        scheduled.toInstant(),
                        "Technical decline cooldown — scheduled for 10:00 AM morning settlement window"
                );
            } else if (attempt == 2) {
                ZonedDateTime scheduled = date.plusDays(1).atTime(MORNING_SETTLEMENT_WINDOW).atZone(IST_ZONE);
                return new SuggestedRetryWindow(
                        scheduled.toInstant(),
                        "Secondary retry attempt — scheduled for next-day 10:00 AM banking window"
                );
            } else {
                ZonedDateTime scheduled = date.plusDays(2).atTime(MORNING_SETTLEMENT_WINDOW).atZone(IST_ZONE);
                return new SuggestedRetryWindow(
                        scheduled.toInstant(),
                        "Final technical retry attempt — scheduled for +2 days 10:00 AM banking window"
                );
            }
        }

        // Default Insufficient Funds (Non-Salary Period): Space out by 1, 3, 7 days at 10:00 AM IST
        if (safeCategory.contains("insufficient")) {
            int dayOffset = switch (attempt) {
                case 1 -> 1;
                case 2 -> 3;
                default -> 7;
            };
            ZonedDateTime scheduled = date.plusDays(dayOffset).atTime(MORNING_SETTLEMENT_WINDOW).atZone(IST_ZONE);
            return new SuggestedRetryWindow(
                    scheduled.toInstant(),
                    String.format("Scheduled for Day +%d 10:00 AM IST morning liquidity window", dayOffset)
            );
        }

        // Generic fallback: Next eligible morning or afternoon window
        LocalDate targetDate = time.isBefore(LocalTime.of(12, 0)) ? date : date.plusDays(1);
        LocalTime targetTime = (targetDate.equals(date) && time.isBefore(LocalTime.of(13, 0)))
                ? AFTERNOON_SETTLEMENT_WINDOW
                : MORNING_SETTLEMENT_WINDOW;

        ZonedDateTime scheduled = targetDate.atTime(targetTime).atZone(IST_ZONE);
        return new SuggestedRetryWindow(
                scheduled.toInstant(),
                "Scheduled within standard Indian banking settlement window (10:00 AM–12:00 PM / 2:00 PM–4:00 PM IST)"
        );
    }

    private static boolean isWithinCbsMaintenanceWindow(LocalTime time) {
        // 23:30 to 23:59:59 OR 00:00 to 03:30
        return (time.equals(CBS_START) || time.isAfter(CBS_START)) ||
               (time.equals(CBS_END) || time.isBefore(CBS_END));
    }

    private static boolean isNearSalaryCreditPeriod(LocalDate date) {
        int day = date.getDayOfMonth();
        int lengthOfMonth = date.lengthOfMonth();
        // 1st, 2nd, 3rd OR last 2 days of month (e.g. 27th/28th in Feb, 29th/30th in Apr, 30th/31st in Mar)
        return (day >= 1 && day <= 3) || (day >= lengthOfMonth - 1);
    }

    private static boolean isWithinUpiPeakWindow(LocalTime time) {
        // 19:00 to 21:30
        return (time.equals(UPI_PEAK_START) || time.isAfter(UPI_PEAK_START)) &&
               (time.equals(UPI_PEAK_END) || time.isBefore(UPI_PEAK_END));
    }
}
