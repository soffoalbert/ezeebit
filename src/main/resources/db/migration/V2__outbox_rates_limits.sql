-- Hardening: transactional outbox for durable payout submission, a shared rate
-- reference for the deviation guard, per-merchant withdrawal limits, and persistence
-- of the quoted mid-rate for reconciliation.

-- Store the raw feed mid-rate alongside the effective (post-spread) rate so a
-- conversion can be reconciled against the market rate at quote time.
ALTER TABLE conversion_quote
    ADD COLUMN mid_rate DECIMAL(38,18) NULL AFTER rate;

-- Transactional outbox. A row is written in the same transaction as the business
-- change (e.g. the withdrawal hold); a relay dispatches it at-least-once and the
-- downstream handler is idempotent, giving effectively exactly-once processing that
-- survives a crash.
CREATE TABLE outbox_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_type  VARCHAR(32)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    payload         JSON         NOT NULL,
    status          VARCHAR(16)  NOT NULL,          -- PENDING, PROCESSING, PROCESSED, FAILED
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    claimed_at      TIMESTAMP(6) NULL,
    last_error      VARCHAR(500) NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at    TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    INDEX ix_outbox_due (status, next_attempt_at)
) ENGINE=InnoDB;

-- Shared last-accepted rate per currency pair, so the deviation guard holds across
-- instances instead of living in one node's memory.
CREATE TABLE rate_observation (
    pair        VARCHAR(24)    NOT NULL,            -- e.g. 'USDT:ZAR'
    rate        DECIMAL(38,18) NOT NULL,
    observed_at TIMESTAMP(6)   NOT NULL,
    PRIMARY KEY (pair)
) ENGINE=InnoDB;

-- Optional per-(merchant, currency) cap on a single withdrawal. When absent, the
-- configured per-currency default applies (or no cap if neither is set).
CREATE TABLE withdrawal_limit (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    merchant_id        BIGINT         NOT NULL,
    currency           VARCHAR(10)    NOT NULL,
    max_per_withdrawal DECIMAL(38,18) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_withdrawal_limit UNIQUE (merchant_id, currency),
    CONSTRAINT fk_withdrawal_limit_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id)
) ENGINE=InnoDB;

-- Example override: merchant 1 may withdraw at most 5,000 USDT per request.
INSERT INTO withdrawal_limit (merchant_id, currency, max_per_withdrawal) VALUES
    (1, 'USDT', 5000.000000);
