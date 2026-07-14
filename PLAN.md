# Ezeebit Take-Home — Implementation Plan

**Stack:** Java 21, Spring Boot 3.x, MySQL 8, Flyway, JUnit 5 + Testcontainers
**Architecture:** Hexagonal (Ports & Adapters)
**Chosen tasks:** Task 1 (hold balances), Task 2 (convert), Task 3 (withdraw)
**Time budget:** ~2–3 hours of implementation; anything cut goes into SOLUTION.md

---

## 1. Why these three tasks

Tasks 1–3 form a single coherent story — the wallet core that holds, converts, and
moves a merchant's money — and each one showcases a different correctness concern
the brief cares about:

| Task | Correctness concern it demonstrates |
|------|--------------------------------------|
| 1 — Hold balances | Double-entry ledger, auditability, no silent currency mixing |
| 2 — Convert | Handling a slow, moving external price; quote/execute separation |
| 3 — Withdraw | Idempotency under duplicate requests, async settlement, holds/reservations |

Tasks 4–6 reuse the same ports (see §8), which lets SOLUTION.md show the design
extends without rework.

## 2. Hexagonal layout

```
src/main/java/com/ezeebit/wallet/
├── domain/                          # pure Java, no Spring, no JPA
│   ├── model/
│   │   ├── Money.java               # value object: BigDecimal amount + Currency
│   │   ├── Currency.java            # enum/VO: ZAR, NGN, KES, USDT, USDC (+ scale)
│   │   ├── Account.java             # (merchantId, currency) balance aggregate
│   │   ├── LedgerEntry.java         # immutable double-entry line
│   │   ├── Conversion.java          # aggregate w/ state machine
│   │   ├── Withdrawal.java          # aggregate w/ state machine
│   │   └── Quote.java               # rate + expiry
│   └── exception/                   # InsufficientFunds, CurrencyMismatch, QuoteExpired…
├── application/
│   ├── port/
│   │   ├── in/                      # use-case interfaces (driving ports)
│   │   │   ├── DepositFundsUseCase.java
│   │   │   ├── GetBalancesUseCase.java
│   │   │   ├── GetLedgerHistoryUseCase.java
│   │   │   ├── QuoteConversionUseCase.java
│   │   │   ├── ExecuteConversionUseCase.java
│   │   │   ├── RequestWithdrawalUseCase.java
│   │   │   └── HandlePayoutResultUseCase.java
│   │   └── out/                     # driven ports
│   │       ├── AccountRepository.java
│   │       ├── LedgerRepository.java
│   │       ├── ConversionRepository.java
│   │       ├── WithdrawalRepository.java
│   │       ├── IdempotencyStore.java
│   │       ├── ExchangeRateProvider.java   # external, assumed to exist
│   │       └── PayoutRail.java             # external, async, assumed to exist
│   └── service/                     # use-case implementations, @Transactional here
│       ├── LedgerService.java       # the ONLY writer of balances + entries
│       ├── ConversionService.java
│       └── WithdrawalService.java
└── adapter/
    ├── in/web/                      # REST controllers + request/response DTOs + error handler
    └── out/
        ├── persistence/             # JPA entities + Spring Data repos + mappers
        ├── exchangerate/            # stub ExchangeRateProvider (configurable delay/failure)
        └── payout/                  # stub PayoutRail + simulated async callback
```

Rules enforced:
- `domain/` has zero framework imports — plain Java, unit-testable in isolation.
- Controllers depend only on `port/in`; persistence implements `port/out`.
- JPA entities live in the adapter and are mapped to/from domain objects — the
  domain model is not the persistence model.

## 3. Data model (MySQL, via Flyway migrations)

```sql
merchant            (id, name, country, created_at)

account             (id, merchant_id, currency, balance DECIMAL(38,18),
                     version BIGINT,                  -- optimistic lock
                     UNIQUE (merchant_id, currency))

ledger_entry        (id, account_id, currency, amount DECIMAL(38,18),  -- signed
                     type ENUM(DEPOSIT, CONVERSION_OUT, CONVERSION_IN,
                               WITHDRAWAL_HOLD, WITHDRAWAL_SETTLE, WITHDRAWAL_RELEASE),
                     operation_id CHAR(36),           -- groups the legs of one operation
                     balance_after DECIMAL(38,18),    -- running balance for audit
                     created_at TIMESTAMP(6))
                     -- append-only: no UPDATE/DELETE ever

conversion          (id, merchant_id, quote_id, from_currency, to_currency,
                     from_amount, to_amount, rate, status ENUM(QUOTED, EXECUTED,
                     EXPIRED, FAILED), quoted_at, quote_expires_at, executed_at)

withdrawal          (id, merchant_id, idempotency_key, currency, amount,
                     destination_json, status ENUM(PENDING, SUBMITTED, COMPLETED,
                     FAILED), payout_reference, created_at, updated_at,
                     UNIQUE (merchant_id, idempotency_key))

idempotency_record  (key_hash, merchant_id, endpoint, request_hash,
                     response_snapshot JSON, created_at,
                     UNIQUE (merchant_id, key_hash, endpoint))
```

Key decisions:
- **DECIMAL(38,18), never floats.** `Money` uses `BigDecimal` with per-currency
  scale (ZAR=2, USDT=6, etc.). Constructor rejects mismatched-currency arithmetic —
  currencies cannot be silently mixed at the type level.
- **Append-only double-entry ledger.** Balance on `account` is a cached projection;
  the ledger is the source of truth. Every operation writes balanced legs sharing
  an `operation_id`, and `SUM(ledger_entry.amount)` per account must always equal
  `account.balance` (asserted in tests). `balance_after` gives the "explain any
  balance at any date" audit trail cheaply.
- **Concurrency:** `SELECT … FOR UPDATE` on the account row inside the operation
  transaction (pessimistic, simple, correct under bursts) + `version` column as a
  belt-and-braces optimistic check. Justify in SOLUTION.md why pessimistic beats
  retry-loops for money at this volume.

## 4. Task 1 — Hold balances (foundation, build first)

Endpoints:
- `POST /merchants/{id}/deposits` — body: currency, amount, `Idempotency-Key` header
- `GET  /merchants/{id}/balances`
- `GET  /merchants/{id}/accounts/{currency}/ledger?from=&to=` — paginated history

Flow (deposit): idempotency check → lock/create account row → append ledger entry
→ update cached balance → store idempotency response snapshot → 201. Replays with
the same key return the stored snapshot with the same status (200/201 semantics
documented).

## 5. Task 2 — Convert

Two-step **quote → execute** flow:
- `POST /merchants/{id}/conversions/quotes` — calls `ExchangeRateProvider`, applies
  a small spread in the platform's favour, persists a `Quote` with a short TTL
  (e.g. 30s), returns quoted rate + expiry.
- `POST /merchants/{id}/conversions` — body: quoteId, `Idempotency-Key`. Validates
  quote not expired/used, locks the source account, checks funds, writes two
  balanced ledger legs (CONVERSION_OUT / CONVERSION_IN) atomically in one DB
  transaction.

Handling the moving/slow rate service:
- The merchant gets **the quoted rate**, never a rate fetched mid-execution — no
  surprise slippage for them; the TTL bounds the platform's exposure.
- Rate provider called with a timeout + one retry; a timeout fails the *quote*
  (cheap), never the *execution*.
- Sanity check on the returned rate (deviation vs last-known rate) to reject a
  "bad exchange rate" from the feed — one of the failure cases the brief asks for.

## 6. Task 3 — Withdraw

Endpoints:
- `POST /merchants/{id}/withdrawals` — `Idempotency-Key` required (natural key for
  the "flaky mobile double-tap" problem)
- `GET  /merchants/{id}/withdrawals/{withdrawalId}`
- `POST /internal/payout-callbacks` — the async rail's success/failure webhook
  (idempotent by payout_reference)

State machine: `PENDING → SUBMITTED → COMPLETED | FAILED`.

Flow:
1. Idempotency check (`UNIQUE(merchant_id, idempotency_key)` — the DB is the final
   arbiter under races; catch duplicate-key and return the original withdrawal).
2. Lock account, verify available funds, write a `WITHDRAWAL_HOLD` ledger entry
   (debit) — funds leave the spendable balance *before* the rail is called, so
   concurrent withdrawals can't double-spend.
3. Commit, then submit to `PayoutRail` (stub). Record `payout_reference`.
4. Callback: on success → `WITHDRAWAL_SETTLE` marker + status COMPLETED; on
   failure → compensating `WITHDRAWAL_RELEASE` credit entry + status FAILED.
   Duplicate callbacks are no-ops (status guard on the state machine).
5. Edge case to document: crash between commit and rail submission → a scheduled
   sweeper re-submits PENDING withdrawals older than a threshold (rail call itself
   idempotent by withdrawal id). Implement if time permits, otherwise SOLUTION.md.

## 7. Cross-cutting

- **Idempotency**: shared `IdempotencyStore` + `Idempotency-Key` header on all
  mutating endpoints; same key + different body → 422.
- **Errors**: RFC-7807 problem responses via `@ControllerAdvice`
  (INSUFFICIENT_FUNDS, QUOTE_EXPIRED, DUPLICATE_REQUEST, …).
- **Stubs for external services**: in-adapter fakes with configurable latency and
  failure rates so failure handling is demonstrable in tests.
- **Config**: `docker-compose.yml` for MySQL; `application.yml` profiles.

## 8. How tasks 4–6 slot in (for SOLUTION.md, no code)

- **Task 4 (incoming crypto):** a new driving adapter — blockchain-confirmation
  webhook → `CreditIncomingPaymentUseCase`. Dedup by (tx_hash, output_index);
  confirmation levels map to a `pending_balance` vs `available_balance` split, i.e.
  new ledger entry types on the *same* ledger. No new architecture needed.
- **Task 5 (auto-settle):** a domain event `FundsCredited` published by
  LedgerService; a policy service holds merchant rules and calls the *existing*
  ExecuteConversion use case (market-rate variant). Retry queue if the rate feed
  is down.
- **Task 6 (payout routing):** `PayoutRail` is already a port — routing becomes a
  composite adapter choosing a partner by country/limits/health, with the same
  hold/settle/release ledger semantics on failure.

## 9. Testing strategy

- Domain unit tests: `Money` arithmetic/mixing rejection, state machines,
  insufficient-funds.
- Service tests with mocked ports: quote expiry, idempotent replay, payout
  failure → release.
- Integration tests (Testcontainers MySQL): the two that matter most —
  (a) N concurrent withdrawals against one balance → exactly the affordable subset
  succeeds; (b) duplicate deposit/withdrawal requests → one ledger effect.
- Ledger invariant test: after any scenario, `SUM(entries) == balance` per account.

## 10. Build order (fits the 2–3 h budget)

1. Skeleton: Spring Boot project, Flyway baseline, docker-compose, hexagonal packages (~20 min)
2. Domain model + LedgerService + deposit/balance endpoints + idempotency (~45 min)
3. Withdrawals incl. hold/release + payout stub + callback (~45 min)
4. Conversions (quote → execute) (~30 min)
5. Concurrency + idempotency integration tests (~20 min)
6. README.md + SOLUTION.md + AI usage notes (~20 min)

If time runs short: cut Task 2 implementation to design-only in SOLUTION.md before
cutting any correctness tests — the brief weighs money-safety over feature count.
