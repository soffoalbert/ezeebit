# SOLUTION

## Tasks chosen

I implemented **Task 1 (hold balances)**, **Task 2 (convert)**, and **Task 3 (withdraw)**.
They form one coherent story — the core wallet that holds, converts, and moves a merchant's
money — and each one exercises a different correctness concern the brief cares about:

| Task | Correctness concern it demonstrates |
|------|--------------------------------------|
| 1 — Hold balances | An auditable double-entry ledger; no silently-mixed currencies |
| 2 — Convert | Handling a slow, moving external price; quote/execute separation |
| 3 — Withdraw | Idempotency under duplicate requests; async settlement; holds & compensation |

Tasks 4–6 reuse the same ports and the same ledger; how they slot in is described at the end.

## Architecture — hexagonal (ports & adapters)

```
domain/        pure Java, no Spring/JPA — Money, Currency, Account, LedgerEntry,
               Quote, Conversion, Withdrawal (+ their invariants and state machines)
application/
  port/in      use-case interfaces the web layer calls (DepositFunds, ExecuteConversion, …)
  port/out     interfaces the domain needs (AccountRepository, ExchangeRateProvider,
               PayoutRail, IdempotencyStore, …)
  service      use-case implementations; transaction boundaries live here
adapter/
  in/web       REST controllers, DTOs, RFC-7807 error handler
  in/scheduling recovery sweeper
  out/persistence  JPA entities + Spring Data repos + mappers (entities are NOT the domain model)
  out/exchangerate stub rate feed (configurable latency/failure)
  out/payout       stub async payout rail + post-commit submission + settlement callback
```

The domain has **zero framework imports** and is unit-testable in isolation. Controllers depend
only on `port/in`; persistence and external systems implement `port/out`. JPA entities are mapped
to/from domain objects, so the persistence model can change without touching the domain.

## The money model (the heart of it)

**Everything is `BigDecimal`, never a float.** `Money` is a value object of `(amount, currency)`
where the amount is normalised to a per-currency scale (fiat = 2 dp, stablecoins = 6 dp). Arithmetic
between two different currencies throws `CurrencyMismatchException` — **currencies cannot be silently
mixed**; it's a construct-time error, not a runtime surprise.

**An append-only, double-entry ledger is the source of truth.** Every balance movement is written
as one or more immutable `ledger_entry` rows sharing an `operation_id`, and the account's `balance`
column is a *cached projection* of those entries. Each entry stores `balance_after` (the running
balance), so any historical balance can be explained cheaply — "how did this reach its value?" is
answered by reading the entries. The invariant `SUM(ledger_entry.amount) == account.balance` holds
for every account and is asserted by every integration test.

`LedgerPostingService` is the **single writer** of balances and entries. It is the only place that
locks an account, applies a movement (enforcing no-overdraft and currency match), updates the cached
balance, and appends the ledger line — atomically, in the caller's transaction.

## Concurrency & correctness

- **Pessimistic row locks** (`SELECT … FOR UPDATE`) on the account row are the serialization point
  for all money movements on that account. For merchant-scoped funds at this volume, a short lock is
  simpler and more predictable than optimistic-retry loops; an optimistic `@Version` column is kept
  as belt-and-braces. The `ConcurrentWithdrawalIT` fires 20 simultaneous withdrawals against a
  balance that affords 10, and asserts exactly 10 succeed and the balance never goes negative.
- **Idempotency** is a single mechanism (`IdempotencyGuard` + `idempotency_record` table), keyed by
  `(merchant, endpoint, Idempotency-Key)`. A sequential retry (the common flaky-mobile case) reads
  the stored response and repeats no effect. A genuine simultaneous double-submit is resolved by the
  unique constraint: the loser's transaction rolls back with a retryable `CONCURRENT_REQUEST` (409),
  and its retry then hits the cache — so there is never a double effect. Same key + different body is
  rejected as `IDEMPOTENCY_CONFLICT` (422).

## Task 2 — conversion, and the moving market

A conversion is **quote → execute**:

1. `POST …/conversions/quotes` calls the rate feed, applies a spread in the platform's favour,
   rounds the merchant's received amount **down**, and persists a `Quote` with a short TTL.
2. `POST …/conversions` executes against that `quoteId`: it locks the quote, checks it is neither
   expired nor already used (a state machine on the quote), debits the source and credits the target
   in one transaction.

Decisions this encodes:

- **Which rate the merchant gets:** the exact rate they were quoted — never a rate re-fetched at
  execution time — so they suffer no surprise slippage. The TTL bounds how long the platform is
  exposed to that locked price.
- **Slow/failing feed:** the feed is called only at *quote* time (cheap), with the failure surfaced
  as `RATE_UNAVAILABLE` (503); execution never depends on a live feed call.
- **Bad rate:** a returned rate that is non-positive, or that deviates from the last-accepted rate by
  more than a configurable threshold, is rejected (`INVALID_RATE`, 502) before it can be quoted.
- **A quote is single-use:** enforced both by the quote state machine and by execute-idempotency, so
  a merchant can never convert against the same quote twice, and never convert more than they hold.

## Task 3 — withdrawal, and never paying out twice

State machine: `PENDING → SUBMITTED → COMPLETED | FAILED`.

1. **Hold first.** On request, funds are debited via a `WITHDRAWAL_HOLD` ledger entry *inside the
   request transaction, before the rail is ever called*. Concurrent or duplicate requests therefore
   cannot double-spend, and a merchant can never withdraw money they don't have.
2. **Submit after commit.** The rail is asynchronous, so we don't call it while holding a DB lock.
   A post-commit event triggers submission on a background thread; the rail call is idempotent on the
   withdrawal id. This guarantees we only ever submit a hold that is safely committed.
3. **Settle.** The rail calls back (webhook, or the in-process stub): success posts a zero-amount
   `WITHDRAWAL_SETTLE` marker for the trail; **failure posts a compensating `WITHDRAWAL_RELEASE`
   credit**, returning the held funds. Duplicate callbacks are no-ops (state-machine guards). The
   `WithdrawalFailureIT` forces a failing rail and asserts the balance is restored.
4. **Crash recovery.** If the process dies between committing the hold and submitting, a scheduled
   sweeper re-submits stale `PENDING` withdrawals; because the rail call is idempotent, re-submission
   is safe.

## Handling unreliable dependencies (a required theme)

- **Rate feed timeout / bad rate:** stubbed with configurable latency and failure injection; handled
  by failing the quote (`RATE_UNAVAILABLE`) and by the deviation/sign sanity check (`INVALID_RATE`).
- **Duplicate notifications:** the payout callback and all mutating endpoints are idempotent
  (state-machine guards + the idempotency store).
- **Duplicate requests over flaky mobile:** the `Idempotency-Key` contract, backed by a unique
  constraint.
- **Failed payout:** compensating ledger release; the balance is always right.

## How the remaining tasks slot into the same design

- **Task 4 (incoming crypto):** add a driving adapter for the blockchain-confirmation webhook →
  a `CreditIncomingPaymentUseCase`. Dedupe by `(tx_hash, output_index)`; confirmation levels map to
  new ledger entry types on the *same* ledger (a pending vs available split). No new architecture.
- **Task 5 (auto-settle):** `LedgerPostingService` publishes a `FundsCredited` domain event; a policy
  service holding each merchant's rule reacts by calling the *existing* `ExecuteConversion` use case
  (a market-rate variant of quote→execute), with a retry queue when the feed is down.
- **Task 6 (payout routing):** `PayoutRail` is already a port. Routing becomes a composite adapter
  that picks a country/partner by limits and health and falls back on failure — with the same
  hold/settle/release ledger semantics, so a partner failure is just a `RELEASE`.

## Assumptions

- A merchant is identified by an id in the request; no auth/KYC (per the brief). Merchants `1`/`2`
  and their zero accounts are seeded by Flyway.
- Deposits and incoming funds are trusted inputs (Task 1 says "money arrives"); real deposits would
  come from Task 4's confirmation flow.
- Rounding on conversion is **against the merchant** (received amount rounded down) so rounding never
  favours them; the spread is the platform's margin.
- The spread is the platform's margin; rounding on conversion is against the merchant.

## Hardening implemented

The following were built out beyond the core three tasks (see the commit history / package
`adapter.out.persistence` and `adapter.in.scheduling`):

- **Transactional outbox for payout submission.** Requesting a withdrawal now writes an
  `outbox_event` row in the *same transaction* as the hold. An `OutboxRelay` claims due events
  with `SELECT … FOR UPDATE SKIP LOCKED`, dispatches each to the idempotent `submit` use case,
  and retries with exponential backoff. This replaces the post-commit event: submission now
  survives a crash at any point (a claimed-but-unfinished event is reclaimed once its claim goes
  stale), and is effectively exactly-once. The recovery sweeper remains as a reconciliation backstop.
- **Shared rate-deviation reference.** The "last accepted rate" moved from an in-memory map to a
  `rate_observation` table (`RateReferenceStore`), so the bad-rate guard holds across instances.
- **Quote reconciliation & expiry.** The raw feed mid-rate is persisted alongside the effective
  rate (`conversion_quote.mid_rate`), and a `QuoteExpiryJob` marks stale `ACTIVE` quotes `EXPIRED`.
- **Observability.** Micrometer metrics (`wallet.ledger.entries` tagged by type/currency,
  `wallet.outbox.processed|failed`), a structured `wallet.audit` log line per ledger movement, and a
  `LedgerInvariantMonitor` that periodically checks `SUM(entries) == balance` and raises an ERROR +
  a `wallet.ledger.invariant.violations` gauge on drift. Actuator exposes `/actuator/health`,
  `/metrics`, and `/prometheus`.
- **Withdrawal limits & destination validation.** A per-(merchant, currency) cap
  (`withdrawal_limit` table) with a configurable per-currency default, plus a `PayoutDestinationValidator`
  that requires a blockchain address for stablecoins and bank details for fiat — rejected up front
  (`WITHDRAWAL_LIMIT_EXCEEDED` / `INVALID_DESTINATION`, 422) before any funds are held.
- **Cursor (keyset) pagination** on the ledger endpoint (`?before=<id>&limit=`), stable under
  concurrent appends, returning a `nextCursor`.
- **Tests:** `@WebMvcTest` slices for the HTTP contract and problem-response shapes, a randomised
  property-style test that hammers the wallet and asserts the ledger invariant, and integration tests
  for the outbox path, the DB rate guard, limits/destination validation, and cursor paging.

## What I'd do with more time

- **Exactly-once rail calls with an idempotency token** exchanged with the payout provider, and a
  dead-letter view for outbox events that reach `FAILED`.
- **Cross-instance idempotency hardening:** the `idempotency_record` unique constraint already makes
  duplicate requests safe across nodes; I'd add request-hash coverage of the full payload and a TTL /
  archival policy for old records.
- **Daily/rolling limits** (not just per-withdrawal), and richer destination validation per chain/bank
  scheme.
- **Delivery of the audit stream** to an append-only sink (e.g. Kafka) and dashboards/alerts on the
  exported metrics.

## Known limitations / not finished

- The stubbed payout rail retries are not modelled end-to-end; a genuinely lost provider callback is
  reconciled only by the recovery sweeper (funds remain safely held meanwhile, never lost).
- Limits are per-withdrawal, not rolling-window; destination validation is structural, not scheme-aware.
- The rate-deviation reference keeps only the last value per pair, not a time-weighted band.
