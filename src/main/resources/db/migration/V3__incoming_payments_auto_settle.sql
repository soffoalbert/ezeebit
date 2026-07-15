-- Task 4/5: accept incoming crypto payments (pending -> available) and auto-settle
-- a percentage of them into the merchant's local currency.
--
-- Design notes:
--   * Pending funds NEVER touch the ledger: account.balance stays the single spendable
--     balance and SUM(ledger_entry.amount) == balance holds trivially. The pending phase
--     lives entirely on incoming_payment; a single INCOMING_CREDIT ledger entry is posted
--     only when the payment reaches the confirmation threshold.
--   * Dedupe is by (tx_hash, output_index): the unique constraint is the arbiter of the
--     first-insert race, and the row's state machine absorbs duplicate/out-of-order
--     confirmation notifications.
--   * Auto-settle conversions execute at market rate without a quote, so quote_id is now
--     nullable and a conversion records what triggered it.

-- Allow quote-less (auto-settle) conversions and record their provenance.
ALTER TABLE conversion
    MODIFY COLUMN quote_id CHAR(36) NULL;
ALTER TABLE conversion
    ADD COLUMN trigger_type        VARCHAR(16) NOT NULL DEFAULT 'QUOTED' AFTER quote_id,
    ADD COLUMN incoming_payment_id CHAR(36)    NULL     AFTER trigger_type,
    ADD INDEX ix_conversion_incoming (incoming_payment_id);

CREATE TABLE incoming_payment (
    id                       CHAR(36)       NOT NULL,
    merchant_id              BIGINT         NOT NULL,
    tx_hash                  VARCHAR(80)    NOT NULL,
    output_index             INT            NOT NULL,
    currency                 VARCHAR(10)    NOT NULL,          -- stablecoin only
    amount                   DECIMAL(38,18) NOT NULL,
    confirmations            INT            NOT NULL DEFAULT 0, -- monotonic max seen
    status                   VARCHAR(16)    NOT NULL,           -- PENDING, CONFIRMED
    credit_operation_id      CHAR(36)       NULL,               -- ledger op of the INCOMING_CREDIT
    settle_status            VARCHAR(16)    NOT NULL DEFAULT 'NONE', -- NONE, REQUESTED, SETTLED, SKIPPED
    settlement_conversion_id CHAR(36)       NULL,
    first_seen_at            TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    confirmed_at             TIMESTAMP(6)   NULL,
    updated_at               TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_incoming_tx_output UNIQUE (tx_hash, output_index),
    CONSTRAINT fk_incoming_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id),
    CONSTRAINT fk_incoming_settle_conversion FOREIGN KEY (settlement_conversion_id) REFERENCES conversion (id),
    INDEX ix_incoming_merchant_status (merchant_id, status, currency)
) ENGINE=InnoDB;

CREATE TABLE auto_settle_rule (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    merchant_id     BIGINT       NOT NULL,
    source_currency VARCHAR(10)  NOT NULL,           -- stablecoin
    target_currency VARCHAR(10)  NOT NULL,           -- merchant's local fiat
    percentage      DECIMAL(7,4) NOT NULL,           -- percent of each incoming amount, (0, 100]
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_auto_settle UNIQUE (merchant_id, source_currency),
    CONSTRAINT fk_auto_settle_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id)
) ENGINE=InnoDB;

-- Seed: merchant 1 (ZA) auto-converts half of every incoming USDT to ZAR.
INSERT INTO auto_settle_rule (merchant_id, source_currency, target_currency, percentage, enabled)
VALUES (1, 'USDT', 'ZAR', 50.0000, 1);
