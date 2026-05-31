# databricks-pg-coexist

**Pattern**: Both Databricks and Postgres DataSources live in the same Spring Boot app. Routing is decided **per-dataset** at startup from yml config (the `DataLocationRegistry`). There is **no fallback** between stores — if a dataset's configured store is down, that dataset's endpoints fail; the other dataset's endpoints keep working. For fallback semantics, see `databricks-pg-fallback/` (variant 3).

## Demo scenario: portfolio holdings

Two datasets, each in its natural store:

| Dataset | Endpoint | Lives in | Why |
|---|---|---|---|
| `live` | `GET /api/holdings/live?account=ACC001` | Postgres (Docker container) | OLTP — fast point reads, low latency |
| `historical` | `GET /api/holdings/historical?account=ACC001&from=2026-01-01&to=2026-05-01` | Databricks (`workspace.demo.historical_holdings`) | OLAP — analytical scans over date ranges |

Both endpoints return a JSON array of `Holding` records with the same shape: `id`, `account`, `symbol`, `quantity`, `avgCost`, `asOfDate`.

## Setup

### 1. Seed the Databricks side

Paste this into the Databricks SQL Editor (uses your existing `workspace.demo` schema):

```sql
CREATE OR REPLACE TABLE workspace.demo.historical_holdings (
    id          BIGINT NOT NULL,
    account     STRING NOT NULL,
    symbol      STRING NOT NULL,
    quantity    DOUBLE NOT NULL,
    avg_cost    DOUBLE NOT NULL,
    as_of_date  DATE   NOT NULL
) USING DELTA;

INSERT INTO workspace.demo.historical_holdings VALUES
    (1,  'ACC001', 'AAPL',  100.0,  142.10, DATE'2026-01-31'),
    (2,  'ACC001', 'MSFT',   50.0,  275.20, DATE'2026-01-31'),
    (3,  'ACC001', 'AAPL',  100.0,  148.40, DATE'2026-02-28'),
    (4,  'ACC001', 'MSFT',   50.0,  285.60, DATE'2026-02-28'),
    (5,  'ACC001', 'AAPL',  100.0,  152.10, DATE'2026-03-31'),
    (6,  'ACC001', 'MSFT',   50.0,  291.80, DATE'2026-03-31'),
    (7,  'ACC002', 'TSLA',  200.0,  208.50, DATE'2026-01-31'),
    (8,  'ACC002', 'NVDA',   75.0,  470.20, DATE'2026-01-31'),
    (9,  'ACC002', 'TSLA',  200.0,  221.80, DATE'2026-02-28'),
    (10, 'ACC002', 'NVDA',   75.0,  495.60, DATE'2026-02-28'),
    (11, 'ACC003', 'AAPL',  300.0,  138.90, DATE'2026-01-31'),
    (12, 'ACC003', 'AMZN',   40.0,  155.30, DATE'2026-01-31'),
    (13, 'ACC003', 'AAPL',  300.0,  145.20, DATE'2026-02-28'),
    (14, 'ACC003', 'AMZN',   40.0,  162.80, DATE'2026-02-28'),
    (15, 'ACC003', 'AAPL',  300.0,  152.40, DATE'2026-03-31');

SELECT COUNT(*) FROM workspace.demo.historical_holdings;  -- 15
```

### 2. Start Postgres via Docker

From the repo root:

```bash
docker compose -f databricks-pg-coexist/docker-compose.yml up -d
```

The Postgres container auto-runs `init/01-schema.sql` and `init/02-seed.sql` on first start — `live_holdings` table created and seeded with 9 rows.

Verify:

```bash
docker compose -f databricks-pg-coexist/docker-compose.yml exec pg \
  psql -U postgres -d holdings -c "SELECT count(*) FROM live_holdings"
# -> 9
```

### 3. Set env vars and run

PowerShell:

```powershell
$env:DATABRICKS_HOST = "dbc-xxxxxxxx-xxxx.cloud.databricks.com"
$env:DATABRICKS_HTTP_PATH = "/sql/1.0/warehouses/abc..."
$env:DATABRICKS_TOKEN = "dapi....your-token..."
mvn -pl databricks-pg-coexist spring-boot:run
```

PG defaults to `jdbc:postgresql://localhost:5432/holdings` with user/pass `postgres`/`postgres` — override via `PG_URL`, `PG_USERNAME`, `PG_PASSWORD` if needed.

### 4. Hit the endpoints

```bash
# Live (Postgres path — fast)
curl 'http://localhost:8080/api/holdings/live?account=ACC001'

# Historical (Databricks path — first call may be 30-60s if warehouse cold-starts)
curl 'http://localhost:8080/api/holdings/historical?account=ACC001&from=2026-01-01&to=2026-05-01'

# Both health components UP
curl http://localhost:8080/actuator/health
```

## How the routing works

`DataLocationRegistry` is a `@ConfigurationProperties("app.holdings.routing")` bean. It loads at startup from yml:

```yaml
app:
  holdings:
    routing:
      datasets:
        live: postgres
        historical: databricks
```

`HoldingsService.getLiveHoldings(...)` does `registry.locationOf("live")` → `POSTGRES` → dispatches to `PgLiveHoldingsRepository`. Same pattern for `historical`. The per-request lookup is a `HashMap.get` — ~50 nanoseconds, free relative to a DB call.

**To flip routing**: change the yml (or the corresponding env vars if you wire them in) and restart. No code change. This demo doesn't have a Databricks-backed live repo or a PG-backed historical repo, so flipping will produce a clear "not configured for this store in this demo" error — that's intentional, showing the registry truly drives dispatch.

For an actual migration scenario (same data mirrored in both stores during cutover), implement both repo variants for each dataset; then flipping the registry seamlessly swaps the read source.

## Trade-offs vs other variants

| Pick this when | Pick `databricks-only` instead | Pick `databricks-pg-fallback` instead |
|---|---|---|
| You have datasets that genuinely belong in different stores (live vs historical, transactional vs analytical) | You only need Databricks; PG isn't in the picture | You want the same data in both stores with automatic failover when Databricks is down |
| Each store is the source of truth for its own dataset; no overlap | — | You're migrating from PG to Databricks and want safety during cutover |

## What's intentionally NOT in this module

To keep the routing pattern visible, this module omits features that `databricks-only/` has:
- Pagination + sort whitelist
- OAuth M2M auth scaffold (PAT only here)
- Configurable query timeout

Adapt those from `databricks-only/` when you take this pattern into a real host app.
