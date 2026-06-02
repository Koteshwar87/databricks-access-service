# databricks-pg-fallback

**Pattern**: Same data lives in **both** Databricks and Postgres. The app always tries Databricks first. If Databricks fails (warehouse down, network blip, or circuit breaker is open), it transparently falls back to Postgres and returns the result. The caller never knows. This is the **Strangler Fig fallback pattern** — useful during PG → Databricks migration cutover, or for production deployments where Databricks availability isn't 100% but PG is more reliable.

Compare to `databricks-pg-coexist/`: that variant has each store own a *different* dataset and no fallback. This variant has *same data in both* with implicit fallback.

## Demo scenario: trade history

`trades` table — executed trades for client accounts. Same shape (`id, account, symbol, side, quantity, price, exec_time`), same 15 rows, in both stores. The Databricks JDBC URL uses `workspace.demo.trades`; PG uses `trades` in the `tradehistory` database.

Single endpoint: `GET /api/trades?account=ACC001` → list of trades for that account, ordered by `exec_time` DESC.

## How the fallback works

The annotation lives on `TradeHistoryService.getTradesByAccount(...)`:

```java
@Retry(name = "databricks")
@CircuitBreaker(name = "databricks", fallbackMethod = "getFromPg")
public List<Trade> getTradesByAccount(String account) {
    return databricksRepo.findByAccount(account);
}

private List<Trade> getFromPg(String account, Throwable cause) {
    log.warn("Databricks failed, falling back to PG: {}", cause.getMessage());
    return pgRepo.findByAccount(account);
}
```

Flow:
1. Call enters `@CircuitBreaker` decorator (outer)
2. Then `@Retry` decorator (inner)
3. Retry attempts up to 3 times with exponential backoff (1s, 2s, 4s)
4. If all retries fail, exception propagates to CircuitBreaker
5. CircuitBreaker records the failure; invokes `getFromPg(account, cause)`
6. `getFromPg` queries PG and returns the result
7. Caller sees a normal response — no error, just maybe slower than usual

If the circuit is already **open** (5+ recent failures), step 1 throws `CallNotPermittedException` immediately. Fallback fires right away — no wasted retry attempts on a known-dead downstream. This is the key value: when Databricks is sustainedly down, the app fast-fails to PG instead of timing out every request.

If **PG also fails**, the PG exception propagates to the controller, gets mapped to HTTP 503 by `TradeHistoryExceptionHandler`. The original Databricks exception is preserved only in the log line emitted by `getFromPg`.

## Setup

### 1. Seed the Databricks side

Paste this into the Databricks SQL Editor:

```sql
CREATE OR REPLACE TABLE workspace.demo.trades (
    id         BIGINT NOT NULL,
    account    STRING NOT NULL,
    symbol     STRING NOT NULL,
    side       STRING NOT NULL,
    quantity   DOUBLE NOT NULL,
    price      DOUBLE NOT NULL,
    exec_time  TIMESTAMP NOT NULL
) USING DELTA;

INSERT INTO workspace.demo.trades VALUES
    (1,  'ACC001', 'AAPL', 'BUY',  100.0, 142.10, TIMESTAMP'2026-01-15 09:31:22'),
    (2,  'ACC001', 'AAPL', 'BUY',   50.0, 148.40, TIMESTAMP'2026-02-10 10:05:48'),
    (3,  'ACC001', 'MSFT', 'BUY',   25.0, 275.20, TIMESTAMP'2026-01-22 11:18:09'),
    (4,  'ACC001', 'MSFT', 'SELL',  10.0, 285.60, TIMESTAMP'2026-03-04 14:42:31'),
    (5,  'ACC001', 'GOOG', 'BUY',   15.0, 2750.10, TIMESTAMP'2026-02-19 13:07:55'),
    (6,  'ACC002', 'TSLA', 'BUY',  200.0, 208.50, TIMESTAMP'2026-01-08 10:11:34'),
    (7,  'ACC002', 'TSLA', 'SELL',  50.0, 221.80, TIMESTAMP'2026-02-26 15:23:17'),
    (8,  'ACC002', 'NVDA', 'BUY',   75.0, 470.20, TIMESTAMP'2026-01-30 09:45:02'),
    (9,  'ACC002', 'NVDA', 'BUY',   25.0, 495.60, TIMESTAMP'2026-03-12 11:32:48'),
    (10, 'ACC002', 'AMD',  'BUY',  150.0, 102.40, TIMESTAMP'2026-02-04 13:51:19'),
    (11, 'ACC003', 'AAPL', 'BUY',  300.0, 138.90, TIMESTAMP'2026-01-11 10:02:43'),
    (12, 'ACC003', 'AAPL', 'BUY',  100.0, 145.20, TIMESTAMP'2026-02-14 09:28:55'),
    (13, 'ACC003', 'AMZN', 'BUY',   40.0, 155.30, TIMESTAMP'2026-01-25 14:17:11'),
    (14, 'ACC003', 'AMZN', 'SELL',  10.0, 162.80, TIMESTAMP'2026-03-07 11:09:28'),
    (15, 'ACC003', 'META', 'BUY',   60.0, 325.80, TIMESTAMP'2026-02-21 15:44:06');

SELECT COUNT(*) FROM workspace.demo.trades;  -- 15
```

### 2. Start Postgres via Docker

> **Port collision note**: if you also have `databricks-pg-coexist`'s container running, stop it first — both bind host port 5432. `docker compose -f databricks-pg-coexist/docker-compose.yml down` clears it.

From the repo root:

```bash
docker compose -f databricks-pg-fallback/docker-compose.yml up -d
```

Init scripts auto-run on first start. Verify:

```bash
docker compose -f databricks-pg-fallback/docker-compose.yml exec pg \
  psql -U postgres -d tradehistory -c "SELECT count(*) FROM trades"
# -> 15
```

### 3. Set env vars and run

PowerShell:

```powershell
$env:DATABRICKS_HOST = "dbc-xxxxxxxx-xxxx.cloud.databricks.com"
$env:DATABRICKS_HTTP_PATH = "/sql/1.0/warehouses/abc..."
$env:DATABRICKS_TOKEN = "dapi....your-token..."
mvn -pl databricks-pg-fallback spring-boot:run
```

### 4. Exercise the three scenarios

**Both stores up — Databricks path**:
```bash
curl 'http://localhost:8080/api/trades?account=ACC001'
# Returns 5 trades for ACC001 from Databricks (first call may be 30-60s cold-start)
# Logs do NOT show "falling back to PG"
```

**Databricks down — fallback path**:
- Stop the SQL warehouse in the Databricks UI
- `curl 'http://localhost:8080/api/trades?account=ACC001'` again
- Response: same 5 trades, slightly slower (retry storm), now from PG
- Logs: `WARN Databricks failed for account=ACC001, falling back to PG: ...`

**Open circuit — fast fallback**:
- With warehouse still stopped, hit the endpoint 5+ times
- Once the circuit opens (~5 failures), subsequent calls return **immediately** with PG data
- `curl http://localhost:8080/actuator/circuitbreakers` shows state `OPEN`
- Logs show fallback firing from `CallNotPermittedException` instead of `DataAccessException`

**Both stores down — HTTP 503**:
- `docker compose -f databricks-pg-fallback/docker-compose.yml stop pg`
- With warehouse also down, endpoint returns HTTP 503

**Recovery**:
- Restart warehouse + PG. Wait 30s (circuit's open window).
- Next call goes back to Databricks (half-open → closed transition). No fallback warning.

## Trade-offs vs other variants

| Pick this when | Pick `databricks-only` instead | Pick `databricks-pg-coexist` instead |
|---|---|---|
| You're migrating PG → Databricks and want safety during cutover (dual writes, fallback reads) | You only need Databricks; PG is gone | Each dataset has a natural home; no overlap |
| Databricks availability is good but not great, and you have PG as a more-reliable backup | Databricks is your only source of truth | You want **explicit** routing per dataset (no automatic failover) |
| Consumers should not see Databricks outages | — | — |

## What's intentionally NOT in this module

To keep the fallback pattern visible, this module omits features that `databricks-only/` has:
- Pagination + sort whitelist
- OAuth M2M auth scaffold (PAT only)
- Configurable query timeout

Adapt those from `databricks-only/` when you take this pattern into a real host app.

It also doesn't include a **data sync mechanism** between Databricks and PG — for the demo we manually seed both. In a real Strangler Fig setup you'd have CDC, event-driven sync, or dual-writes on the producer side keeping the two stores in agreement.
