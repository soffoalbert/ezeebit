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

Tasks 4–6 are implemented on the same ports and the same ledger; each has its own section below.

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
- **Rate feed down at auto-settle:** the failure propagates, the payment stays `REQUESTED`, and the
  outbox retries with backoff until the feed recovers — no partial settlement (Task 5).
- **Payout partner down:** priority failover to the next partner; if all are unhealthy the funds stay
  held and are retried, and a payout that can never route is failed-and-released past its deadline so
  money is never stuck (Task 6).

## Task 4 — accept an incoming crypto payment (pending → available)

The confirmation service notifies us (at-least-once, possibly out of order) as an on-chain payment
gains confirmations. Each notification locks-or-inserts an `incoming_payment` row keyed by
`(tx_hash, output_index)` — the unique constraint is the arbiter of the first-insert race (a lost
race becomes a retryable `CONCURRENT_REQUEST`, exactly like the idempotency store).

- **Pending never touches the ledger.** `account.balance` stays the single spendable balance and the
  invariant `SUM(entries) == balance` holds trivially. A first sighting is a `PENDING` row: visible
  on `GET /balances` as `pendingIncoming` (summed from `incoming_payment`, not the ledger), but
  unspendable. Only when the confirmation count reaches `wallet.incoming.confirmation-threshold`
  (default 3) is a single `INCOMING_CREDIT(+1)` posted, making the funds spendable.
- **Counted exactly once.** Confirmations are folded in as a monotonic maximum, so a duplicate or
  regressed notification is a silent no-op; `confirm(...)` is single-shot (PENDING → CONFIRMED), so
  the credit is posted exactly once no matter how the notifications interleave. A replay whose
  immutable facts (merchant/currency/amount) disagree with the recorded row is a `409
  INCOMING_PAYMENT_CONFLICT`.
- **Decoupling stays outbox-only.** There are no Spring domain events anywhere in this codebase, so
  rather than introduce a `FundsCredited` event, confirming a payment appends one
  `INCOMING_PAYMENT_CONFIRMED` outbox event *in the same transaction* as the credit. The relay drives
  auto-settle — free crash-safety, consistent with Task 3's payout submission.

## Task 5 — auto-settle at market rate

An `auto_settle_rule` (one per `(merchant, source_currency)`, seeded: merchant 1 converts 50% of
incoming USDT to ZAR) is applied when funds become available. The outbox dispatches
`INCOMING_PAYMENT_CONFIRMED` to `AutoSettleApplicationService`, which:

- **Prices at execution time**, reusing the exact same `ConversionPricer` as merchant quotes —
  the same sign/deviation guard against a bad feed, the same 0.005 spread in the platform's favour,
  the same round-DOWN on the received amount. The conversion is quote-less: `conversion.quote_id`
  became nullable and rows carry a `trigger_type` (`QUOTED` | `AUTO_SETTLE`) and back-link to the
  `incoming_payment`. The portion is `amount × percentage` rounded DOWN so the platform never
  over-converts; a rounds-to-zero portion is recorded as `SKIPPED`.
- **Is idempotent via a state machine, not a lock alone.** The payment's `settle_status`
  (`NONE → REQUESTED → SETTLED | SKIPPED`) is row-locked; an outbox redelivery finds it no longer
  `REQUESTED` and does nothing, so it can never double-convert.
- **Is crash-safe when the feed is down.** `RateUnavailable`/`InvalidRate` propagate, the transaction
  rolls back leaving the payment `REQUESTED`, and the outbox retries with exponential backoff
  (10 attempts ≈ up to ~1h) until the feed recovers — no money moved, nothing lost.

## Task 6 — payout routing with failover

A DB-backed `payout_partner` registry (one row per `(partner, currency)` route: `code`, nullable
`country`, `currency`, nullable `per_tx_limit`, `healthy`, `priority`) replaces the single rail.
Routing is an application service (`PayoutRoutingService`), not a composite adapter: the decision is
business policy that deserves visibility, attribution and unit tests, so `PayoutRail` stays pure
transport and now takes the chosen partner.

- **Fail fast, before holding funds.** `request()` checks a *structural* route exists (country +
  currency + limit, ignoring health); if none can ever serve it, that's a `422 NO_PAYOUT_ROUTE` and
  no funds are held (e.g. a KES payout — there is deliberately no KES partner).
- **Priority failover.** `submit()` tries eligible partners in priority order; a partner that is
  synchronously unavailable (`PARTNER_UNAVAILABLE`) triggers failover to the next (a low-limit
  priority-1 ZA partner also demonstrates limit-bypass to the fallback for large amounts).
- **Transient vs terminal is the careful branch.** If nothing is eligible: all-unhealthy is
  *transient* — throw so the withdrawal stays `PENDING` with funds held and the outbox/sweeper retry;
  a structural change (route genuinely gone) is *terminal* — `markFailed` + `WITHDRAWAL_RELEASE`.
- **Money is never stuck.** The recovery sweeper fails-and-releases a `PENDING` payout older than
  `wallet.payout.pending-deadline-ms` instead of retrying forever. A partner failure is otherwise
  just the existing hold/settle/release ledger dance, so nothing new is needed for correctness.
- **Reference** is `payout_<partnerCode>_<withdrawalId>` — deterministic per partner + withdrawal, so
  re-submitting the same withdrawal to the same partner is idempotent on the rail side.

## Assumptions

- A merchant is identified by an id in the request; no auth/KYC (per the brief). Merchants `1`/`2`
  and their zero accounts are seeded by Flyway.
- The `POST /deposits` endpoint is a trusted test seam for putting money in (Task 1 says "money
  arrives"); real on-chain deposits arrive through Task 4's confirmation flow, which is implemented.
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
- **Blockchain reorgs are out of scope.** An incoming payment is `PENDING` then terminal `CONFIRMED`;
  a reorg that un-confirms a credited payment is not handled. The extension point is an `ORPHANED`
  status plus a compensating `INCOMING_REVERSAL(-1)` ledger entry (and unwinding any auto-settle),
  which the existing state machine and single-writer ledger are shaped to accommodate.
- **Auto-settle after the credit is spent.** If a merchant spends the just-credited stablecoin before
  the relay runs auto-settle, the `CONVERSION_OUT` debit hits `InsufficientFunds`; the outbox retries
  and eventually exhausts, leaving the payment stuck `REQUESTED`. Acceptable for the brief (funds are
  never lost, only unsettled); a real system would settle synchronously at confirm time or reserve
  the portion.
- **Cross-partner double-submit on a crash between accept-and-commit.** Because the reference is
  per-partner, a crash after a partner accepts but before we commit `SUBMITTED` could, on retry, route
  to a *different* partner and double-pay — the same class of residual risk as the original single-rail
  design. A shared idempotency token exchanged with the provider would close it.
