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
- The rate-deviation guard's "last rate" is per-instance and in-memory — fine for a single node, and
  called out below as something to move to a shared store.

## What I'd do with more time

- **Money-only unit tests are thorough, but** I'd add controller (`@WebMvcTest`) tests for the HTTP
  contract and problem-response shapes, and a property-based test for the ledger invariant.
- **Outbox for the payout submission** instead of a post-commit event, so submission survives a crash
  without relying solely on the sweeper, and to make the rail call exactly-once end-to-end.
- **Move the rate-deviation reference and any cross-instance idempotency assumptions** to a shared
  store (Redis/DB) so the safety checks hold across multiple nodes.
- **Quote signing / persistence of the mid-rate** for post-hoc reconciliation, and a background job to
  expire stale `ACTIVE` quotes.
- **Observability:** structured audit events, metrics on hold/settle/release counts, and alerts on the
  ledger invariant drifting.
- **Per-merchant/currency limits and validation** of destination formats (chain address vs bank).

## Known limitations / not finished

- The rate-deviation guard is in-memory (single-node). 
- Withdrawal submission relies on a post-commit event plus a sweeper rather than a transactional
  outbox; under a crash at exactly the wrong moment, submission is delayed to the next sweep (funds
  remain safely held, never lost).
- No pagination cursor on the ledger endpoint beyond `limit`/`offset`.
