package com.ezeebit.wallet;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base for tests that need a real MySQL (row locks, unique constraints, JSON columns).
 *
 * <p>Uses the Testcontainers "singleton container" pattern: the container is started once
 * in a static initializer and kept up for the whole JVM (stopped by Testcontainers' JVM
 * shutdown hook). This is deliberately NOT the {@code @Container}/{@code @Testcontainers}
 * lifecycle, which would stop the shared container after the first test class's
 * {@code afterAll} while later classes reuse the cached Spring context still pointing at it.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("wallet")
            .withUsername("wallet")
            .withPassword("wallet");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // Deterministic exchange-rate stub during tests. Payout knobs are left at their
        // application.yml defaults (no failures) and overridden per-test where needed.
        registry.add("wallet.exchange-rate.latency-ms", () -> "0");
        registry.add("wallet.exchange-rate.failure-rate", () -> "0.0");
        // Quiet the background pollers: multiple cached Spring contexts share one database,
        // so an always-on relay in one context would race events belonging to another.
        // Tests that exercise the async path drive the relay explicitly instead.
        registry.add("wallet.outbox.poll-interval-ms", () -> "3600000");
        registry.add("wallet.payout.sweeper-interval-ms", () -> "3600000");
        registry.add("wallet.conversion.expiry-sweep-ms", () -> "3600000");
        registry.add("wallet.ledger.invariant-check-ms", () -> "3600000");
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetData() {
        // Start each test from clean balances/ledger while keeping the seeded merchants,
        // accounts, and withdrawal limits.
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM ledger_entry");
        jdbc.update("DELETE FROM incoming_payment");   // FK to conversion — delete before conversion
        jdbc.update("DELETE FROM conversion");
        jdbc.update("DELETE FROM conversion_quote");
        jdbc.update("DELETE FROM withdrawal");
        jdbc.update("DELETE FROM idempotency_record");
        jdbc.update("DELETE FROM rate_observation");
        jdbc.update("UPDATE account SET balance = 0, version = 0");

        // Auto-settle rules are seeded reference data; reset to the single seeded rule
        // (merchant 1: USDT -> ZAR 50%) so each test starts from a known configuration.
        jdbc.update("DELETE FROM auto_settle_rule");
        jdbc.update("INSERT INTO auto_settle_rule (merchant_id, source_currency, target_currency, "
                + "percentage, enabled) VALUES (1, 'USDT', 'ZAR', 50.0000, 1)");

        // Payout partners are seeded reference data; only reset the health toggled by tests.
        jdbc.update("UPDATE payout_partner SET healthy = TRUE");
    }

    /**
     * The core ledger invariant: for every account, the sum of signed ledger amounts
     * equals the cached balance.
     */
    protected void assertLedgerInvariantHolds() {
        var rows = jdbc.queryForList(
                "SELECT a.id, a.balance, COALESCE((SELECT SUM(le.amount) FROM ledger_entry le " +
                        "WHERE le.account_id = a.id), 0) AS ledger_sum FROM account a");
        for (var row : rows) {
            BigDecimal balance = (BigDecimal) row.get("balance");
            BigDecimal ledgerSum = (BigDecimal) row.get("ledger_sum");
            assertThat(balance).as("account %s balance vs ledger sum", row.get("id"))
                    .isEqualByComparingTo(ledgerSum);
        }
    }

    protected BigDecimal balanceOf(long merchantId, String currency) {
        return jdbc.queryForObject(
                "SELECT balance FROM account WHERE merchant_id = ? AND currency = ?",
                BigDecimal.class, merchantId, currency);
    }
}
