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

# Task 4 — accept an incoming crypto payment (pending -> available at N confirmations)
TX="0x$(date +%s)"
curl -X POST $B/internal/incoming-payments -H 'Content-Type: application/json' \
  -d "{\"merchantId\":1,\"txHash\":\"$TX\",\"outputIndex\":0,\"currency\":\"USDT\",\"amount\":\"100.000000\",\"confirmations\":1}"
curl $B/merchants/1/balances            # shows pendingIncoming=100, balance unchanged
curl -X POST $B/internal/incoming-payments -H 'Content-Type: application/json' \
  -d "{\"merchantId\":1,\"txHash\":\"$TX\",\"outputIndex\":0,\"currency\":\"USDT\",\"amount\":\"100.000000\",\"confirmations\":3}"
curl $B/merchants/1/incoming-payments   # CONFIRMED; funds now spendable

# Task 5 — auto-settle: merchant 1 is seeded to convert 50% of incoming USDT to ZAR at market rate.
curl $B/merchants/1/auto-settle-rules
# ... a moment after the payment confirms, half the USDT is converted to ZAR (settleStatus=SETTLED).
curl -X PUT $B/merchants/1/auto-settle-rules/USDT -H 'Content-Type: application/json' \
  -d '{"targetCurrency":"ZAR","percentage":"75.0","enabled":true}'

# Task 6 — payout routing: a ZAR withdrawal routes to a partner by country/currency/limit/health.
curl -X POST $B/merchants/1/withdrawals -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: wd-zar-1' \
  -d '{"currency":"ZAR","amount":"500.00","destination":{"accountNumber":"12345678","bankCode":"632005"}}'
# small ZAR -> za-swift-eft (priority 1); >1000 ZAR bypasses its cap -> za-payfast.
curl $B/internal/payout-partners
curl -X PATCH $B/internal/payout-partners/za-swift-eft -H 'Content-Type: application/json' \
  -d '{"healthy":false}'          # toggling health demonstrates live failover
# A KES withdrawal has no partner -> 422 NO_PAYOUT_ROUTE.
```

## API collection

A ready-to-run collection lives in [`bruno-collection/`](bruno-collection/) (Postman v2.1 JSON,
so it imports into Postman or runs with Newman, and converts to Bruno):

- `bruno-collection/ezeebit-wallet.bruno_collection.json` — every endpoint with happy-path and
  error/edge cases (idempotency replay & conflict, quote reuse, insufficient funds, over-limit,
  invalid destination, missing header, incoming-payment replay/conflict, auto-settle wait,
  payout failover, no-route), chained via variables (`quoteId`, `withdrawalId`, `txHash`, …) and
  with test assertions on status codes and RFC-7807 `code`s. Folders are grouped by task.
- `bruno-collection/ezeebit-local.bruno_environment.json` — points at `http://localhost:8080`, merchant `1`.

Run the folders top-to-bottom in Postman, or from the CLI with
[Newman](https://github.com/postmanlabs/newman):

```bash
npx newman run bruno-collection/ezeebit-wallet.bruno_collection.json \
  -e bruno-collection/ezeebit-local.bruno_environment.json
```

## API

All mutating money endpoints require an `Idempotency-Key` header; retrying with the same key
returns the original result instead of repeating the effect.

| Method & path | Purpose |
|---|---|
| `POST /merchants/{id}/deposits` | Deposit funds (Task 1) |
| `GET  /merchants/{id}/balances` | All balances for a merchant (Task 1) |
| `GET  /merchants/{id}/accounts/{currency}/ledger` | Audit history, cursor-paginated `?before=<id>&limit=` (Task 1) |
| `POST /merchants/{id}/conversions/quotes` | Get a rate quote, locked for a short TTL (Task 2) |
| `POST /merchants/{id}/conversions` | Execute a conversion against a quote id (Task 2) |
| `POST /merchants/{id}/withdrawals` | Request a payout; holds funds immediately, routes to a partner (Task 3/6) |
| `GET  /merchants/{id}/withdrawals/{withdrawalId}` | Withdrawal status, incl. routed `partnerCode` (Task 3/6) |
| `POST /internal/payout-callbacks` | Payout-rail settlement webhook (Task 3) |
| `POST /internal/incoming-payments` | Blockchain confirmation webhook; pending→available (Task 4) |
| `GET  /merchants/{id}/incoming-payments` | List incoming payments (Task 4) |
| `GET  /merchants/{id}/auto-settle-rules` | List auto-settle rules (Task 5) |
| `PUT  /merchants/{id}/auto-settle-rules/{sourceCurrency}` | Upsert an auto-settle rule (Task 5) |
| `GET  /internal/payout-partners` | List payout partner registry (Task 6) |
| `PATCH /internal/payout-partners/{code}` | Toggle a partner's health, e.g. `{"healthy":false}` (Task 6) |

The `GET /merchants/{id}/balances` response now includes `pendingIncoming` per currency — money
seen on-chain but not yet confirmed (visible, unspendable, never on the ledger).

Errors are returned as RFC-7807 problem responses with a stable `code`, e.g.
`INSUFFICIENT_FUNDS` (422), `QUOTE_EXPIRED` (409), `RATE_UNAVAILABLE` (503),
`WITHDRAWAL_LIMIT_EXCEEDED` (422), `INVALID_DESTINATION` (422),
`INCOMING_PAYMENT_CONFLICT` (409), `NO_PAYOUT_ROUTE` (422), `PARTNER_UNAVAILABLE` (503),
`PARTNER_NOT_FOUND` (404).

Supported currencies: `ZAR`, `NGN`, `KES` (fiat), `USDT`, `USDC` (stablecoin).

Withdrawal destinations are validated by currency: stablecoins require an `address`
(e.g. `{"address":"chain-address-000"}`), fiat requires `accountNumber` and `bankCode`.
A per-(merchant, currency) cap applies (merchant `1` is seeded at 5,000 USDT per withdrawal).

## Observability

Actuator exposes `/actuator/health`, `/actuator/metrics`, and `/actuator/prometheus`. Notable
metrics: `wallet.ledger.entries` (tagged by `type` and `currency`),
`wallet.outbox.processed` / `wallet.outbox.failed`, and `wallet.ledger.invariant.violations`
(a gauge that must stay `0`). Every balance movement also emits a structured `wallet.audit` log line.

Payout submission uses a **transactional outbox**: a withdrawal request writes an `outbox_event`
in the same transaction as the fund hold, and a background relay submits it to the rail with
retry/backoff — so submission survives a crash and is never lost.

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
| `wallet.payout.pending-deadline-ms` | How long a held payout may stay PENDING before the sweeper fails + releases it |
| `wallet.payout.partners.<code>.{failure-rate,unavailable,settlement-delay-ms}` | Per-partner stub overrides (fall back to the globals); `unavailable=true` forces failover |
| `wallet.withdrawal.max-per-currency` | Default per-currency single-withdrawal cap (per-merchant DB override wins) |
| `wallet.incoming.confirmation-threshold` | On-chain confirmations before an incoming payment becomes spendable (Task 4) |
| `wallet.outbox.poll-interval-ms` | How often the outbox relay polls for due events |

## Tests

```bash
mvn verify
```

- **Unit tests** (`*Test`): domain rules — `Money` arithmetic and currency-mixing rejection,
  no-overdraft, and the withdrawal state machine — plus `@WebMvcTest` slices for the HTTP contract
  and RFC-7807 problem responses.
- **Integration tests** (`*IT`, Testcontainers + real MySQL): concurrent withdrawals never
  overdraw, duplicate deposits/withdrawals apply once, conversion quote→execute is idempotent
  and single-use, a failed payout releases the held funds, the outbox relay drives submission and
  settlement, the shared rate guard rejects a wild rate move, withdrawal limits and destination
  validation are enforced, and cursor pagination walks the ledger. Task 4/5/6 add: incoming
  payments credit exactly once through duplicate/out-of-order/conflicting notifications
  (`IncomingPaymentIT`); auto-settle converts at market rate and is crash-safe under a rate-feed
  outage (`AutoSettleIT`, `AutoSettleRetryIT`); payouts route by country/currency/limit/health
  with priority failover, hold-and-retry when partners are down, terminal release past the
  deadline, and `NO_PAYOUT_ROUTE` for an unroutable currency (`PayoutRoutingIT`, `PayoutFailoverIT`).
  A randomised property-style test hammers the wallet with mixed operations. Every integration
  test asserts the ledger invariant `SUM(ledger entries) == account balance`.

> **Note on recent Docker Engine:** the integration tests pin the Docker Engine API version
> to `1.44` (via `maven-failsafe-plugin`) because Docker Engine 29+ raised its minimum
> supported API version above the default negotiated by the bundled Testcontainers client.
> Override with `-Ddocker.api.version=<version>` if your engine differs.
