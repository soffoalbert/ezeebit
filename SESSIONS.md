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

<!-- Template for subsequent sessions:

## Session N — YYYY-MM-DD — <short title>

**What I asked for:**

**What the AI produced:**

**Accepted / changed / rejected:**

**My notes / decisions carried forward:**

-->
