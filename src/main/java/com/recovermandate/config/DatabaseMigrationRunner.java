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
        backfillRecoveryActionVersion();
        backfillCustomerEmails();
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
                    "UPDATE customers SET email = CONCAT('customer.', id, '@example.com') WHERE email IS NULL OR TRIM(email) = ''"
            );
            if (updated > 0) {
                log.info("Successfully backfilled placeholder email for {} pre-existing Customer records without email", updated);
            }
        } catch (Exception e) {
            log.warn("Database migration backfill note for customer email: {}", e.getMessage());
        }
    }
}
