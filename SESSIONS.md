# SESSIONS.md — AI Interaction Log

This file documents how I used AI tooling (Claude Code) during this assignment:
what I asked for, what it produced, and where I accepted, changed, or rejected
its output. It doubles as the "AI usage notes" deliverable requested in the brief.

---

## Session 1 — 2026-07-14 — Assignment analysis & implementation plan

**What I asked for:**
Read the take-home PDF and produce an implementation plan using Hexagonal
Architecture with Java, Spring Boot, and MySQL; set up this log file.

**What the AI produced:**
- Read and summarised `Ezeebit Backend Take-Home Assignment.pdf`.
- Proposed choosing **Tasks 1, 2, and 3** (hold balances, convert, withdraw) on
  the grounds that they form one coherent wallet core and each exercises a
  distinct correctness concern (ledger auditability, moving external prices,
  idempotency under async settlement).
- Wrote `PLAN.md`: hexagonal package layout (pure domain, ports in/out, web +
  persistence + stub adapters), MySQL schema (append-only double-entry ledger,
  `DECIMAL(38,18)`, optimistic version column + `SELECT … FOR UPDATE`),
  quote→execute conversion flow with TTL, withdrawal state machine with
  hold/settle/release ledger entries, idempotency-key strategy, testing strategy
  (Testcontainers, concurrency tests, ledger-sum invariant), and a build order
  sized to the 2–3 hour budget.

**Accepted / changed / rejected:**
- Accepted: the task selection (1 + 2 + 3) and the hexagonal package layout.
- Accepted: append-only double-entry ledger with the balance as a cached projection, and
  `DECIMAL(38,18)` everywhere.
- Carried forward to implementation: quote→execute for conversions, hold/settle/release for
  withdrawals, and a single idempotency mechanism.

**My notes / decisions carried forward:**
- Confirmed tasks 1 + 2 + 3.

---

## Session 2 — 2026-07-14 — Full implementation

**What I asked for:**
Implement the plan end to end (Spring Boot hexagonal app, MySQL, tests, README, SOLUTION).

**What the AI produced:**
- The complete codebase: domain (`Money`, `Currency`, `Account`, `LedgerEntry`, `Quote`,
  `Conversion`, `Withdrawal` + exceptions), application ports and services
  (`LedgerPostingService`, `AccountApplicationService`, `ConversionApplicationService`,
  `WithdrawalApplicationService`, `IdempotencyGuard`), adapters (JPA persistence, stub
  exchange-rate feed, stub async payout rail with post-commit submission + settlement
  callback, REST controllers, RFC-7807 error handler), Flyway migration, and config.
- Tests: domain unit tests plus Testcontainers integration tests for concurrent withdrawals,
  deposit/withdrawal/conversion idempotency, and failed-payout release, each asserting the
  ledger invariant. Final result: **12 unit + 9 integration tests green**, plus a manual
  end-to-end HTTP smoke test of every flow.

**Accepted / changed / rejected (where my judgement overrode the AI / fixed its output):**
- **Rejected the naive JPA merge:** the first cut of the account persistence adapter rebuilt a
  detached entity on every save, which nulled `created_at` on update (the domain `Account`
  doesn't carry it). Caught by an integration test; fixed by marking the column
  `updatable = false` so the merge never overwrites it.
- **Changed the Testcontainers lifecycle:** the initial `@Container` + `@Testcontainers`
  approach stopped the shared container after the first test class while later classes reused
  the cached Spring context pointing at it (connection-refused). Switched to the singleton
  container pattern (start once in a static initializer).
- **Diagnosed environment issues the AI's defaults didn't anticipate:** a stale
  `~/.testcontainers.properties` pinned `docker.host` to a dead TCP proxy (repaired, backup at
  `~/.testcontainers.properties.bak`), and Docker Engine 29 rejects the client's default API
  version — pinned to `1.44` via the Failsafe plugin.
- **Accepted** the core design decisions from Session 1's plan; they held up under
  implementation without rework, which is the main signal the ports/ledger design was sound.

**My notes / decisions carried forward:**
- Remaining known limitations captured in [SOLUTION.md](SOLUTION.md) (in-memory rate-deviation
  guard, post-commit submission vs transactional outbox).

## Session 3 — 2026-07-14 — Hardening (outbox, observability, limits)

**What I asked for:**
Implement everything I'd listed under "What I'd do with more time" and "Known limitations /
not finished" in SOLUTION.md.

**What the AI produced:**
- **Transactional outbox** for payout submission (`outbox_event` table, `OutboxRelay` using
  `FOR UPDATE SKIP LOCKED` + backoff), replacing the post-commit event; recovery sweeper kept as a
  backstop.
- **Shared rate-deviation reference** (`rate_observation` table) replacing the in-memory map.
- **Quote mid-rate persistence** and a **quote-expiry job**.
- **Observability:** Micrometer metrics, a `wallet.audit` structured log per ledger movement, a
  scheduled **ledger-invariant monitor**, and actuator/Prometheus endpoints.
- **Per-(merchant, currency) withdrawal limits** and a **payout destination validator**.
- **Cursor (keyset) pagination** on the ledger endpoint.
- **Tests:** `@WebMvcTest` slices, a randomised property-style invariant test, and integration tests
  for the outbox, rate guard, limits/validation, and pagination. Final: **32 tests green**, plus a
  live HTTP smoke test of every new behaviour.

**Accepted / changed / rejected (where my judgement corrected the AI):**
- **Rejected the AI's first callback-retry fix.** To handle a callback arriving before the SUBMITTED
  state committed, it made the stub rail retry callbacks — which, in the shared test context,
  flooded the scheduler retrying callbacks for rows deleted between tests and broke a passing test.
  Replaced it with a simple settlement delay that guarantees ordering.
- **Diagnosed a cross-context test-isolation bug the AI initially missed:** Spring caches the
  `@SpringBootTest` context across classes, so a cached context's always-on outbox relay raced
  another context's events on the shared DB (submitting a "should-fail" withdrawal as success).
  Fixed by quieting the schedulers under test and driving the relay explicitly where async behaviour
  is asserted.
- **Rejected a broken monitoring query** (`HAVING` without `GROUP BY`) in favour of a correlated
  subquery in `WHERE`.
- **Accepted** the outbox/observability/limits designs; they slotted onto the existing ports cleanly,
  confirming the hexagonal boundaries held up under a second, larger change.

**My notes / decisions carried forward:**
- Remaining future work (exactly-once provider tokens, rolling-window limits, audit sink) is in the
  updated [SOLUTION.md](SOLUTION.md).

## Session 3 — 2026-07-15 — Tasks 4, 5 & 6 at full parity

**What I asked for:**
Implement Tasks 4 (accept an incoming crypto payment), 5 (auto-settle), and 6 (payout routing)
to the same standard as Tasks 1–3: domain + ports + services + adapters, Flyway migrations,
unit + integration tests, collection requests, and README/SOLUTION updates.

**What the AI produced:**
- **Task 4:** `IncomingPayment` aggregate (monotonic-max confirmations, single-shot confirm,
  conflict guard) + `incoming_payment` table with `(tx_hash, output_index)` dedupe; pending funds
  kept entirely off the ledger, surfaced as `pendingIncoming` on balances; a single
  `INCOMING_CREDIT` posted at the confirmation threshold, appending an outbox event in the same
  transaction. Webhook + list endpoints.
- **Task 5:** extracted `ConversionPricer` (shared by quotes and auto-settle so the spread,
  deviation guard, and round-DOWN are identical); `AutoSettleApplicationService` driven by the
  outbox with a `settle_status` state machine for idempotency and rate-feed-outage retry; quote-less
  `conversion` rows (nullable `quote_id`, `trigger_type`, `incoming_payment_id`); rule upsert/list API.
- **Task 6:** DB-backed `payout_partner` registry + `PayoutRoutingService` (structural pre-check
  before holding funds, priority failover, transient-vs-terminal classification), `PayoutRail` now
  takes the chosen partner, per-partner stub knobs, a deadline sweeper that releases stuck payouts,
  and a partner-health admin API.
- Full test parity: `IncomingPaymentIT`, `AutoSettleIT`, `AutoSettleRetryIT`, `PayoutRoutingIT`,
  `PayoutFailoverIT`, domain/slice unit tests; `mvn verify` green across all Tasks 1–6.

**Accepted / changed / rejected:**
- **Accepted** the pending-never-on-the-ledger split and the outbox-only decoupling (no Spring domain
  events), keeping the `SUM(entries) == balance` invariant untouched.
- **Changed** the plan's routing-as-composite-adapter idea to a routing *application service*, so the
  business policy is visible, attributed, and unit-tested while `PayoutRail` stays pure transport.
- **Fixed a latent bug found via a flaky cross-test failure:** `@Scheduled(fixedDelay…)` fires once at
  context startup regardless of the interval, so each test context's background relay raced `resetData`.
  Added `initialDelayString` (tied to the same interval property) to all four schedulers.

**My notes / decisions carried forward:**
- Documented reorg handling (`ORPHANED` extension point), the spend-before-settle edge, and the
  cross-partner double-submit residual risk in [SOLUTION.md](SOLUTION.md).

<!-- Template for subsequent sessions:

## Session N — YYYY-MM-DD — <short title>

**What I asked for:**

**What the AI produced:**

**Accepted / changed / rejected:**

**My notes / decisions carried forward:**

-->
