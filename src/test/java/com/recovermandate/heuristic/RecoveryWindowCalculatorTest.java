package com.recovermandate.heuristic;

import com.recovermandate.heuristic.RecoveryWindowCalculator.SuggestedRetryWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryWindowCalculatorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    @DisplayName("Rule 1 (CBS Window Overnight): 11:45 PM failure avoids CBS and schedules for 10:00 AM next day")
    void testCbsMaintenanceWindow_LateNight() {
        // 2026-08-15 23:45:00 IST
        ZonedDateTime failureIst = ZonedDateTime.of(2026, 8, 15, 23, 45, 0, 0, IST);
        Instant failureInstant = failureIst.toInstant();

        SuggestedRetryWindow window = RecoveryWindowCalculator.calculateOptimalWindow(
                failureInstant, "technical_decline", 1
        );

        assertNotNull(window);
        ZonedDateTime scheduledIst = window.scheduledAt().atZone(IST);

        // Expected: 2026-08-16 10:00:00 IST
        assertEquals(LocalDate.of(2026, 8, 16), scheduledIst.toLocalDate());
        assertEquals(LocalTime.of(10, 0), scheduledIst.toLocalTime());
        assertTrue(window.reason().contains("PSU bank CBS"));
    }

    @Test
    @DisplayName("Rule 1 (CBS Window Early Morning): 02:30 AM failure schedules for 10:00 AM same day")
    void testCbsMaintenanceWindow_EarlyMorning() {
        // 2026-08-16 02:30:00 IST
        ZonedDateTime failureIst = ZonedDateTime.of(2026, 8, 16, 2, 30, 0, 0, IST);
        Instant failureInstant = failureIst.toInstant();

        SuggestedRetryWindow window = RecoveryWindowCalculator.calculateOptimalWindow(
                failureInstant, "technical_decline", 1
        );

        assertNotNull(window);
        ZonedDateTime scheduledIst = window.scheduledAt().atZone(IST);

        // Expected: 2026-08-16 10:00:00 IST
        assertEquals(LocalDate.of(2026, 8, 16), scheduledIst.toLocalDate());
        assertEquals(LocalTime.of(10, 0), scheduledIst.toLocalTime());
        assertTrue(window.reason().contains("PSU bank CBS"));
    }

    @Test
    @DisplayName("Rule 2 (Salary Credit Window): Insufficient funds on 1st of month schedules for 2nd at 10:00 AM")
    void testSalaryCreditWindow_MonthStart() {
        // 2026-09-01 14:00:00 IST (1st of month, midday)
        ZonedDateTime failureIst = ZonedDateTime.of(2026, 9, 1, 14, 0, 0, 0, IST);
        Instant failureInstant = failureIst.toInstant();

        SuggestedRetryWindow window = RecoveryWindowCalculator.calculateOptimalWindow(
                failureInstant, "insufficient_funds", 1
        );

        assertNotNull(window);
        ZonedDateTime scheduledIst = window.scheduledAt().atZone(IST);

        // Expected: 2026-09-02 10:00:00 IST
        assertEquals(LocalDate.of(2026, 9, 2), scheduledIst.toLocalDate());
        assertEquals(LocalTime.of(10, 0), scheduledIst.toLocalTime());
        assertTrue(window.reason().contains("salary credit"));
    }

    @Test
    @DisplayName("Rule 2 (Salary Credit Window): Insufficient funds on 30th (month-end) schedules for next day 10:00 AM")
    void testSalaryCreditWindow_MonthEnd() {
        // 2026-04-30 11:00:00 IST (30th of 30-day month, April)
        ZonedDateTime failureIst = ZonedDateTime.of(2026, 4, 30, 11, 0, 0, 0, IST);
        Instant failureInstant = failureIst.toInstant();

        SuggestedRetryWindow window = RecoveryWindowCalculator.calculateOptimalWindow(
                failureInstant, "insufficient_funds", 1
        );

        assertNotNull(window);
        ZonedDateTime scheduledIst = window.scheduledAt().atZone(IST);

        // Expected: 2026-05-01 10:00:00 IST
        assertEquals(LocalDate.of(2026, 5, 1), scheduledIst.toLocalDate());
        assertEquals(LocalTime.of(10, 0), scheduledIst.toLocalTime());
        assertTrue(window.reason().contains("salary credit"));
    }

    @Test
    @DisplayName("Rule 3 (Peak UPI Window): Failure at 8:00 PM (20:00 IST) avoids congestion and shifts to 10:00 AM next morning")
    void testPeakUpiWindow() {
        // 2026-08-15 20:00:00 IST
        ZonedDateTime failureIst = ZonedDateTime.of(2026, 8, 15, 20, 0, 0, 0, IST);
        Instant failureInstant = failureIst.toInstant();

        SuggestedRetryWindow window = RecoveryWindowCalculator.calculateOptimalWindow(
                failureInstant, "unknown", 1
        );

        assertNotNull(window);
        ZonedDateTime scheduledIst = window.scheduledAt().atZone(IST);

        // Expected: 2026-08-16 10:00:00 IST
        assertEquals(LocalDate.of(2026, 8, 16), scheduledIst.toLocalDate());
        assertEquals(LocalTime.of(10, 0), scheduledIst.toLocalTime());
        assertTrue(window.reason().contains("UPI evening traffic"));
    }

    @Test
    @DisplayName("Rule 4 (Daytime Technical Decline): Failure at 10:30 AM schedules for 2:00 PM afternoon liquidity window")
    void testDaytimeTechnicalDecline_AfternoonSlot() {
        // 2026-08-15 10:30:00 IST
        ZonedDateTime failureIst = ZonedDateTime.of(2026, 8, 15, 10, 30, 0, 0, IST);
        Instant failureInstant = failureIst.toInstant();

        SuggestedRetryWindow window = RecoveryWindowCalculator.calculateOptimalWindow(
                failureInstant, "technical_decline", 1
        );

        assertNotNull(window);
        ZonedDateTime scheduledIst = window.scheduledAt().atZone(IST);

        // Expected: 2026-08-15 14:00:00 IST (2:00 PM same day)
        assertEquals(LocalDate.of(2026, 8, 15), scheduledIst.toLocalDate());
        assertEquals(LocalTime.of(14, 0), scheduledIst.toLocalTime());
        assertTrue(window.reason().contains("afternoon settlement window"));
    }

    @Test
    @DisplayName("Attempt Spacing: Insufficient funds (mid-month) spaces attempts across 1, 3, 7 days")
    void testAttemptSpacing_InsufficientFunds() {
        // 2026-08-15 11:00:00 IST (15th is mid-month)
        ZonedDateTime failureIst = ZonedDateTime.of(2026, 8, 15, 11, 0, 0, 0, IST);
        Instant failureInstant = failureIst.toInstant();

        SuggestedRetryWindow attempt1 = RecoveryWindowCalculator.calculateOptimalWindow(failureInstant, "insufficient_funds", 1);
        SuggestedRetryWindow attempt2 = RecoveryWindowCalculator.calculateOptimalWindow(failureInstant, "insufficient_funds", 2);
        SuggestedRetryWindow attempt3 = RecoveryWindowCalculator.calculateOptimalWindow(failureInstant, "insufficient_funds", 3);

        assertEquals(LocalDate.of(2026, 8, 16), attempt1.scheduledAt().atZone(IST).toLocalDate()); // +1 day
        assertEquals(LocalDate.of(2026, 8, 18), attempt2.scheduledAt().atZone(IST).toLocalDate()); // +3 days
        assertEquals(LocalDate.of(2026, 8, 22), attempt3.scheduledAt().atZone(IST).toLocalDate()); // +7 days
    }
}
