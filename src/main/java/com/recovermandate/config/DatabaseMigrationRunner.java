package com.recovermandate.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Startup migration and data backfill runner.
 * Ensures data integrity across schema evolutions (e.g. backfilling optimistic locking version columns).
 */
@Slf4j
@Component
@Order(1)
public class DatabaseMigrationRunner implements ApplicationRunner {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (jdbcTemplate == null) {
            log.debug("No JdbcTemplate available in context; skipping startup database migrations.");
            return;
        }
        ensureSchemaColumnsExist();
        deduplicatePaymentEvents();
        backfillRecoveryActionVersion();
        backfillCustomerEmails();
        backfillDemoDataFlags();
        deduplicateRetrySchedules();
        backfillCompletedRecoveryActions();
    }

    private void deduplicatePaymentEvents() {
        try {
            log.info("Running database migration check: Deduplicating payment_events by razorpay_payment_id...");
            // Delete dependent retry_schedules for duplicate payment events
            jdbcTemplate.update(
                    "DELETE FROM retry_schedules WHERE payment_event_id NOT IN (" +
                    "  SELECT min_id FROM (SELECT MIN(id) AS min_id FROM payment_events GROUP BY LOWER(razorpay_payment_id)) AS tmp" +
                    ")"
            );

            // Delete dependent payment_links for duplicate recovery_actions
            jdbcTemplate.update(
                    "DELETE FROM payment_links WHERE recovery_action_id IN (" +
                    "  SELECT id FROM recovery_actions WHERE failure_classification_id IN (" +
                    "    SELECT id FROM failure_classifications WHERE payment_event_id NOT IN (" +
                    "      SELECT min_id FROM (SELECT MIN(id) AS min_id FROM payment_events GROUP BY LOWER(razorpay_payment_id)) AS tmp" +
                    "    )" +
                    "  )" +
                    ")"
            );

            // Delete dependent recovery_actions for duplicate failure_classifications
            jdbcTemplate.update(
                    "DELETE FROM recovery_actions WHERE failure_classification_id IN (" +
                    "  SELECT id FROM failure_classifications WHERE payment_event_id NOT IN (" +
                    "    SELECT min_id FROM (SELECT MIN(id) AS min_id FROM payment_events GROUP BY LOWER(razorpay_payment_id)) AS tmp" +
                    "  )" +
                    ")"
            );

            // Delete duplicate failure_classifications
            jdbcTemplate.update(
                    "DELETE FROM failure_classifications WHERE payment_event_id NOT IN (" +
                    "  SELECT min_id FROM (SELECT MIN(id) AS min_id FROM payment_events GROUP BY LOWER(razorpay_payment_id)) AS tmp" +
                    ")"
            );

            // Delete duplicate payment_events
            int deleted = jdbcTemplate.update(
                    "DELETE FROM payment_events WHERE id NOT IN (" +
                    "  SELECT min_id FROM (SELECT MIN(id) AS min_id FROM payment_events GROUP BY LOWER(razorpay_payment_id)) AS tmp" +
                    ")"
            );
            if (deleted > 0) {
                log.info("Successfully cleaned up {} duplicate PaymentEvent records from database", deleted);
            }
        } catch (Exception e) {
            log.warn("Database migration notice for payment_events deduplication: {}", e.getMessage());
        }
    }

    private void backfillCompletedRecoveryActions() {
        try {
            log.info("Running database migration check: Resolving completed recovery actions for recovered subscriptions...");
            int updated = jdbcTemplate.update(
                    "UPDATE recovery_actions SET status = 'COMPLETED' " +
                    "WHERE status IN ('DRAFTED', 'PENDING_DRAFT', 'BLOCKED', 'APPROVED', 'DISPATCHED') " +
                    "AND failure_classification_id IN (" +
                    "    SELECT fc.id FROM failure_classifications fc " +
                    "    JOIN payment_events pe ON fc.payment_event_id = pe.id " +
                    "    WHERE pe.subscription_id IN (" +
                    "        SELECT DISTINCT pe2.subscription_id FROM recovery_actions ra2 " +
                    "        JOIN failure_classifications fc2 ON ra2.failure_classification_id = fc2.id " +
                    "        JOIN payment_events pe2 ON fc2.payment_event_id = pe2.id " +
                    "        WHERE ra2.status = 'RECOVERED' AND pe2.subscription_id IS NOT NULL" +
                    "    )" +
                    ")"
            );
            if (updated > 0) {
                log.info("Successfully transitioned {} sibling recovery actions to COMPLETED", updated);
            }
        } catch (Exception e) {
            log.warn("Database migration note for completed recovery actions: {}", e.getMessage());
        }
    }

    private void deduplicateRetrySchedules() {
        try {
            log.info("Running database migration check: Deduplicating retry_schedules...");
            int deleted = jdbcTemplate.update(
                    "DELETE FROM retry_schedules WHERE id NOT IN (" +
                    "  SELECT min_id FROM (SELECT MIN(id) AS min_id FROM retry_schedules GROUP BY payment_event_id, attempt_number) AS tmp" +
                    ")"
            );
            if (deleted > 0) {
                log.info("Successfully cleaned up {} duplicate RetrySchedule records from database", deleted);
            }
        } catch (Exception e) {
            log.warn("Database migration notice for retry_schedules deduplication: {}", e.getMessage());
        }
    }

    private void ensureSchemaColumnsExist() {
        log.info("Running database migration check: Ensuring required schema columns exist...");
        safeExecuteDdl("ALTER TABLE payment_events ADD COLUMN IF NOT EXISTS is_demo_data BOOLEAN NOT NULL DEFAULT FALSE");
        safeExecuteDdl("ALTER TABLE recovery_actions ADD COLUMN IF NOT EXISTS is_demo_data BOOLEAN NOT NULL DEFAULT FALSE");
        safeExecuteDdl("ALTER TABLE payment_links ADD COLUMN IF NOT EXISTS is_demo_data BOOLEAN NOT NULL DEFAULT FALSE");
        safeExecuteDdl("ALTER TABLE retry_schedules ADD COLUMN IF NOT EXISTS is_demo_data BOOLEAN NOT NULL DEFAULT FALSE");
        
        safeExecuteDdl("CREATE INDEX IF NOT EXISTS idx_payment_events_is_demo ON payment_events(is_demo_data)");
        safeExecuteDdl("CREATE INDEX IF NOT EXISTS idx_recovery_actions_is_demo ON recovery_actions(is_demo_data)");
        safeExecuteDdl("CREATE INDEX IF NOT EXISTS idx_payment_links_is_demo ON payment_links(is_demo_data)");
        safeExecuteDdl("CREATE INDEX IF NOT EXISTS idx_retry_schedules_is_demo ON retry_schedules(is_demo_data)");
    }

    private void safeExecuteDdl(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.debug("Schema migration DDL notice for [{}]: {}", sql, e.getMessage());
        }
    }

    private void backfillRecoveryActionVersion() {
        try {
            log.info("Running database migration check: RecoveryAction optimistic lock version backfill...");
            int updated = jdbcTemplate.update("UPDATE recovery_actions SET version = 0 WHERE version IS NULL");
            if (updated > 0) {
                log.info("Successfully backfilled version = 0 for {} pre-existing RecoveryAction records", updated);
            } else {
                log.debug("No RecoveryAction records with NULL version found.");
            }
        } catch (Exception e) {
            log.warn("Database migration backfill note for recovery_actions version: {}", e.getMessage());
        }
    }

    private void backfillCustomerEmails() {
        try {
            log.info("Running database migration check: Customer email backfill...");
            int updated = jdbcTemplate.update(
                    "UPDATE customers SET email = 'sujaypaul2711@gmail.com', name = 'Sujay Paul' WHERE email IS NULL OR TRIM(email) = '' OR LOWER(email) LIKE '%void%' OR LOWER(email) LIKE '%@razorpay.com%' OR email LIKE 'demo.customer%' OR email LIKE 'customer.%@example.com'"
            );
            jdbcTemplate.update(
                    "UPDATE customers SET name = 'Sujay Paul' WHERE name IS NULL OR TRIM(name) = '' OR LOWER(name) LIKE '%void%'"
            );
            if (updated > 0) {
                log.info("Successfully backfilled placeholder email for {} pre-existing Customer records", updated);
            } else {
                log.debug("No pre-existing Customer records needing email backfill found.");
            }
        } catch (Exception e) {
            log.warn("Database migration backfill note for customer email: {}", e.getMessage());
        }
    }

    private void backfillDemoDataFlags() {
        try {
            log.info("Running database migration check: is_demo_data flags backfill...");
            // 1. Reset real live Razorpay payment events to is_demo_data = false
            jdbcTemplate.update("UPDATE payment_events SET is_demo_data = false WHERE razorpay_payment_id NOT LIKE 'pay_demo_%'");
            // 2. Only actual demo simulation payloads are marked is_demo_data = true
            jdbcTemplate.update("UPDATE payment_events SET is_demo_data = true WHERE razorpay_payment_id LIKE 'pay_demo_%' OR (raw_payload IS NOT NULL AND raw_payload LIKE '%acc_demo_%')");
            
            // 3. Re-align recovery_actions
            jdbcTemplate.update("UPDATE recovery_actions SET is_demo_data = false WHERE actor NOT LIKE 'DEMO%'");
            jdbcTemplate.update("UPDATE recovery_actions ra SET is_demo_data = true WHERE ra.actor LIKE 'DEMO%' OR EXISTS (SELECT 1 FROM failure_classifications fc JOIN payment_events pe ON fc.payment_event_id = pe.id WHERE fc.id = ra.failure_classification_id AND (pe.is_demo_data = true OR pe.razorpay_payment_id LIKE 'pay_demo_%'))");
            
            // 4. Re-align payment_links
            jdbcTemplate.update("UPDATE payment_links SET is_demo_data = false WHERE razorpay_link_id NOT LIKE 'plink_demo_%' AND razorpay_link_id NOT LIKE 'plink_sim_%'");
            jdbcTemplate.update("UPDATE payment_links pl SET is_demo_data = true WHERE pl.razorpay_link_id LIKE 'plink_demo_%' OR pl.razorpay_link_id LIKE 'plink_sim_%' OR EXISTS (SELECT 1 FROM recovery_actions ra WHERE ra.id = pl.recovery_action_id AND ra.is_demo_data = true)");
            
            // 5. Re-align retry_schedules
            jdbcTemplate.update("UPDATE retry_schedules rs SET is_demo_data = (SELECT pe.is_demo_data FROM payment_events pe WHERE pe.id = rs.payment_event_id)");
            log.info("Successfully completed is_demo_data backfill across operational tables.");
        } catch (Exception e) {
            log.warn("Database migration backfill note for is_demo_data: {}", e.getMessage());
        }
    }
}
