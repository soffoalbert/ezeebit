-- Task 6: payout routing. A DB-backed registry of payout partners; a withdrawal is routed
-- to a partner by (merchant country, currency, per-transaction limit, health) with priority
-- failover. One row per (partner, currency) route: a multi-currency partner has one code per
-- currency. A NULL country means "any country" (on-chain rails that settle anywhere).

CREATE TABLE payout_partner (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(40)  NOT NULL,           -- stable identifier, e.g. 'za-swift-eft'
    name         VARCHAR(120) NOT NULL,
    country      VARCHAR(2)   NULL,               -- ISO-3166 alpha-2; NULL = any country
    currency     VARCHAR(10)  NOT NULL,
    per_tx_limit DECIMAL(38,18) NULL,             -- max amount per payout; NULL = no cap
    healthy      TINYINT(1)   NOT NULL DEFAULT 1,
    priority     INT          NOT NULL,           -- lower is tried first
    PRIMARY KEY (id),
    CONSTRAINT uq_payout_partner_code UNIQUE (code),
    INDEX ix_payout_route (currency, country, healthy, priority)
) ENGINE=InnoDB;

ALTER TABLE withdrawal
    ADD COLUMN partner_code VARCHAR(40) NULL AFTER payout_reference;

-- Seeds. ZAR has two partners: a low-limit priority-1 EFT rail (demonstrates limit bypass to
-- the fallback) and a general priority-2 rail. NGN mirrors that with two healthy partners.
-- On-chain stablecoin rails have NULL country (settle regardless of merchant country).
-- Deliberately NO KES partner exists, so a KES payout demonstrates NO_PAYOUT_ROUTE.
INSERT INTO payout_partner (code, name, country, currency, per_tx_limit, healthy, priority) VALUES
    ('za-swift-eft',   'SWIFT EFT (ZA)',        'ZA', 'ZAR', 1000.00, 1, 1),
    ('za-payfast',     'PayFast (ZA)',          'ZA', 'ZAR', NULL,    1, 2),
    ('ng-paystack',    'Paystack (NG)',         'NG', 'NGN', NULL,    1, 1),
    ('ng-flutterwave', 'Flutterwave (NG)',      'NG', 'NGN', NULL,    1, 2),
    ('chain-usdt',     'On-chain USDT',         NULL, 'USDT', NULL,   1, 1),
    ('chain-usdc',     'On-chain USDC',         NULL, 'USDC', NULL,   1, 1);
