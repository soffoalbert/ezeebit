-- Ezeebit merchant wallet — initial schema.
-- Design notes:
--   * All monetary columns are DECIMAL(38,18): exact, never floating point.
--   * `ledger_entry` is append-only and is the source of truth. `account.balance`
--     is a cached projection; SUM(ledger_entry.amount) per account must always
--     equal account.balance (verified by tests).
--   * String ids (CHAR(36)) are UUIDs assigned by the application for aggregates
--     that are referenced by external systems (conversions, withdrawals, quotes).

CREATE TABLE merchant (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    country     VARCHAR(2)   NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE account (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT         NOT NULL,
    currency    VARCHAR(10)    NOT NULL,
    balance     DECIMAL(38,18) NOT NULL DEFAULT 0,
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_account_merchant_currency UNIQUE (merchant_id, currency),
    CONSTRAINT fk_account_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id)
) ENGINE=InnoDB;

CREATE TABLE ledger_entry (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    account_id    BIGINT         NOT NULL,
    merchant_id   BIGINT         NOT NULL,
    currency      VARCHAR(10)    NOT NULL,
    amount        DECIMAL(38,18) NOT NULL,          -- signed: credits positive, debits negative
    type          VARCHAR(32)    NOT NULL,
    operation_id  CHAR(36)       NOT NULL,          -- groups the legs of one business operation
    balance_after DECIMAL(38,18) NOT NULL,          -- running balance after this entry (audit trail)
    reference     VARCHAR(255)   NULL,              -- external ref (payout id, tx hash, ...)
    created_at    TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES account (id),
    INDEX ix_ledger_account_time (account_id, created_at, id),
    INDEX ix_ledger_operation (operation_id)
) ENGINE=InnoDB;

CREATE TABLE conversion_quote (
    id            CHAR(36)       NOT NULL,
    merchant_id   BIGINT         NOT NULL,
    from_currency VARCHAR(10)    NOT NULL,
    to_currency   VARCHAR(10)    NOT NULL,
    from_amount   DECIMAL(38,18) NOT NULL,
    to_amount     DECIMAL(38,18) NOT NULL,
    rate          DECIMAL(38,18) NOT NULL,          -- effective rate incl. spread: to = from * rate
    status        VARCHAR(16)    NOT NULL,          -- ACTIVE, USED, EXPIRED
    created_at    TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at    TIMESTAMP(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_quote_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id)
) ENGINE=InnoDB;

CREATE TABLE conversion (
    id            CHAR(36)       NOT NULL,
    merchant_id   BIGINT         NOT NULL,
    quote_id      CHAR(36)       NOT NULL,
    from_currency VARCHAR(10)    NOT NULL,
    to_currency   VARCHAR(10)    NOT NULL,
    from_amount   DECIMAL(38,18) NOT NULL,
    to_amount     DECIMAL(38,18) NOT NULL,
    rate          DECIMAL(38,18) NOT NULL,
    status        VARCHAR(16)    NOT NULL,          -- EXECUTED, FAILED
    operation_id  CHAR(36)       NOT NULL,
    created_at    TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    executed_at   TIMESTAMP(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_conversion_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id),
    CONSTRAINT fk_conversion_quote FOREIGN KEY (quote_id) REFERENCES conversion_quote (id)
) ENGINE=InnoDB;

CREATE TABLE withdrawal (
    id              CHAR(36)       NOT NULL,
    merchant_id     BIGINT         NOT NULL,
    idempotency_key VARCHAR(120)   NULL,            -- for traceability; idempotency enforced by idempotency_record
    currency        VARCHAR(10)    NOT NULL,
    amount          DECIMAL(38,18) NOT NULL,
    destination     JSON           NOT NULL,
    status          VARCHAR(16)    NOT NULL,        -- PENDING, SUBMITTED, COMPLETED, FAILED
    payout_reference VARCHAR(120)  NULL,
    failure_reason  VARCHAR(255)   NULL,
    operation_id    CHAR(36)       NOT NULL,
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_withdrawal_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id),
    INDEX ix_withdrawal_merchant (merchant_id),
    INDEX ix_withdrawal_payout_ref (payout_reference),
    INDEX ix_withdrawal_status (status)
) ENGINE=InnoDB;

-- Generic idempotency store: one row per (merchant, endpoint, client key).
-- The unique constraint is the final arbiter under concurrent duplicate requests.
CREATE TABLE idempotency_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    merchant_id   BIGINT       NOT NULL,
    endpoint      VARCHAR(64)  NOT NULL,
    idem_key      VARCHAR(120) NOT NULL,
    request_hash  VARCHAR(64)  NOT NULL,            -- to detect same key + different body
    response_json JSON         NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_idempotency UNIQUE (merchant_id, endpoint, idem_key)
) ENGINE=InnoDB;

-- Seed merchants and their accounts so the demo/tests have known ids.
INSERT INTO merchant (id, name, country) VALUES
    (1, 'iStore Cape Town', 'ZA'),
    (2, 'Sunbet Online',    'NG');

INSERT INTO account (merchant_id, currency, balance) VALUES
    (1, 'ZAR', 0), (1, 'NGN', 0), (1, 'KES', 0), (1, 'USDT', 0), (1, 'USDC', 0),
    (2, 'ZAR', 0), (2, 'NGN', 0), (2, 'KES', 0), (2, 'USDT', 0), (2, 'USDC', 0);
