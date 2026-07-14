# Ezeebit Wallet Service

A merchant multi-currency wallet that **holds**, **converts**, and **moves** money, built
for the Ezeebit backend take-home. It implements Tasks **1 (hold balances)**, **2 (convert)**,
and **3 (withdraw)** from the brief. See [SOLUTION.md](SOLUTION.md) for the design and
trade-offs, and [SESSIONS.md](SESSIONS.md) for AI-usage notes.

- **Stack:** Java 21, Spring Boot 3.3, MySQL 8, Flyway, JPA/Hibernate
- **Architecture:** Hexagonal (ports & adapters) — a framework-free domain, use-case ports
  in `application`, and web/persistence/external adapters in `adapter`.

## Prerequisites

- JDK 21+
- Maven 3.9+
- Docker (for the MySQL database and for the integration tests)

## Run it locally

1. Start MySQL:

   ```bash
   docker compose up -d
   ```

2. Run the app (Flyway creates the schema and seeds merchants `1` and `2` on startup):

   ```bash
   mvn spring-boot:run
   # or: mvn -DskipTests package && java -jar target/wallet-0.1.0.jar
   ```

The service listens on `http://localhost:8080`.

## Try it (end-to-end)

```bash
B=http://localhost:8080

# Task 1 — deposit and hold balances (Idempotency-Key makes retries safe)
curl -X POST $B/merchants/1/deposits -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: dep-1' -d '{"currency":"USDT","amount":"500.000000"}'

curl $B/merchants/1/balances

# Task 2 — convert: get a quote, then execute against that exact rate
Q=$(curl -s -X POST $B/merchants/1/conversions/quotes -H 'Content-Type: application/json' \
  -d '{"fromCurrency":"USDT","toCurrency":"ZAR","amount":"100.000000"}')
QID=$(echo "$Q" | python3 -c "import sys,json;print(json.load(sys.stdin)['quoteId'])")
curl -X POST $B/merchants/1/conversions -H 'Content-Type: application/json' \
  -H "Idempotency-Key: conv-1" -d "{\"quoteId\":\"$QID\"}"

# Task 3 — withdraw (async): funds are held immediately, settled later
W=$(curl -s -X POST $B/merchants/1/withdrawals -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: wd-1' \
  -d '{"currency":"USDT","amount":"200.000000","destination":{"chain":"tron","address":"TXYZ"}}')
WID=$(echo "$W" | python3 -c "import sys,json;print(json.load(sys.stdin)['withdrawalId'])")
curl $B/merchants/1/withdrawals/$WID

# Audit trail — explains how any balance reached its value
curl "$B/merchants/1/accounts/USDT/ledger"
```

## API

All mutating money endpoints require an `Idempotency-Key` header; retrying with the same key
returns the original result instead of repeating the effect.

| Method & path | Purpose |
|---|---|
| `POST /merchants/{id}/deposits` | Deposit funds (Task 1) |
| `GET  /merchants/{id}/balances` | All balances for a merchant (Task 1) |
| `GET  /merchants/{id}/accounts/{currency}/ledger` | Audit history for one account (Task 1) |
| `POST /merchants/{id}/conversions/quotes` | Get a rate quote, locked for a short TTL (Task 2) |
| `POST /merchants/{id}/conversions` | Execute a conversion against a quote id (Task 2) |
| `POST /merchants/{id}/withdrawals` | Request a payout; holds funds immediately (Task 3) |
| `GET  /merchants/{id}/withdrawals/{withdrawalId}` | Withdrawal status (Task 3) |
| `POST /internal/payout-callbacks` | Payout-rail settlement webhook (Task 3) |

Errors are returned as RFC-7807 problem responses with a stable `code`, e.g.
`INSUFFICIENT_FUNDS` (422), `QUOTE_EXPIRED` (409), `RATE_UNAVAILABLE` (503).

Supported currencies: `ZAR`, `NGN`, `KES` (fiat), `USDT`, `USDC` (stablecoin).

## Tunable knobs (for exercising failure handling)

The external exchange-rate feed and payout rail are stubbed so their failure modes can be
driven from config (`src/main/resources/application.yml`, prefix `wallet.*`):

| Property | Meaning |
|---|---|
| `wallet.conversion.quote-ttl-seconds` | How long a quote stays valid |
| `wallet.conversion.spread` | Spread taken in the platform's favour on each quote |
| `wallet.conversion.max-rate-deviation` | Reject a feed rate that jumps more than this |
| `wallet.exchange-rate.latency-ms` / `failure-rate` | Inject slowness / timeouts in the rate feed |
| `wallet.payout.settlement-delay-ms` / `failure-rate` | Simulate async settlement delay / failed payouts |

## Tests

```bash
mvn verify
```

- **Unit tests** (`*Test`): domain rules — `Money` arithmetic and currency-mixing rejection,
  no-overdraft, and the withdrawal state machine.
- **Integration tests** (`*IT`, Testcontainers + real MySQL): concurrent withdrawals never
  overdraw, duplicate deposits/withdrawals apply once, conversion quote→execute is idempotent
  and single-use, and a failed payout releases the held funds. Every integration test asserts
  the ledger invariant `SUM(ledger entries) == account balance`.

> **Note on recent Docker Engine:** the integration tests pin the Docker Engine API version
> to `1.44` (via `maven-failsafe-plugin`) because Docker Engine 29+ raised its minimum
> supported API version above the default negotiated by the bundled Testcontainers client.
> Override with `-Ddocker.api.version=<version>` if your engine differs.
