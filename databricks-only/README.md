# databricks-only

**Pattern**: Single Databricks DataSource. The app reads exclusively from Databricks via JDBC — no Postgres, no fallback, no per-dataset routing. Simplest of the three reference variants. For multi-DataSource setups see `databricks-pg-coexist/` and `databricks-pg-fallback/`.

This module is also the most **production-polished** of the three: it carries pieces (OAuth M2M auth scaffold, pagination + sort whitelist, configurable query timeout) that the other modules intentionally omit to keep their patterns visible. When porting one of the other patterns into a host app, lift those forward from here.

## Demo scenario: market indices

`workspace.demo.market_indices` — 9 global stock indices (SPX, NDX, DJI, NSEI, BSESN, FTSE, DAX, N225, HSI). Read-only.

## API endpoints

- `GET /api/indices` — paginated list. Spring `Page` envelope. Supports:
  - `?page=` (default `0`), `?size=` (default `20`)
  - `?sort=field,dir` (e.g. `?sort=symbol,asc`) — sort fields are **whitelisted** in `MarketIndexRepository.SORTABLE_COLUMNS`; an unknown field returns HTTP 400
  - `?country=` — optional filter
- `GET /api/indices/{symbol}` — single result; HTTP 404 if not found
- `GET /actuator/health` — overall health (HTTP 503 when any component is DOWN)
- `GET /actuator/health/databricks` — Databricks-only component with `responseTimeMs` detail
- `GET /actuator/circuitbreakers` — state of all circuit breakers
- `GET /actuator/circuitbreakerevents` — recent circuit-breaker transitions

## Setup

### 1. Seed Databricks

Paste this into the Databricks SQL Editor:

```sql
CREATE OR REPLACE TABLE workspace.demo.market_indices (
    symbol   STRING NOT NULL,
    name     STRING NOT NULL,
    country  STRING NOT NULL,
    value    DOUBLE,
    currency STRING
) USING DELTA;

INSERT INTO workspace.demo.market_indices VALUES
    ('SPX',   'S&P 500',                        'US',     5234.18, 'USD'),
    ('NDX',   'Nasdaq 100',                     'US',    18342.92, 'USD'),
    ('DJI',   'Dow Jones Industrial Average',   'US',    39145.50, 'USD'),
    ('NSEI',  'Nifty 50',                       'IN',    22147.00, 'INR'),
    ('BSESN', 'BSE SENSEX',                     'IN',    72987.03, 'INR'),
    ('FTSE',  'FTSE 100',                       'UK',     7715.36, 'GBP'),
    ('DAX',   'DAX',                            'DE',    17974.14, 'EUR'),
    ('N225',  'Nikkei 225',                     'JP',    39523.55, 'JPY'),
    ('HSI',   'Hang Seng Index',                'HK',    16529.48, 'HKD');

SELECT COUNT(*) FROM workspace.demo.market_indices;  -- 9
```

### 2. Set env vars

**Required**:
- `DATABRICKS_HOST` — server hostname (e.g., `dbc-xxxxxxxx-xxxx.cloud.databricks.com`)
- `DATABRICKS_HTTP_PATH` — SQL warehouse HTTP path (e.g., `/sql/1.0/warehouses/abc123...`)

**Optional** (defaults shown):
- `DATABRICKS_CATALOG` — Unity Catalog catalog name (default: `workspace`)
- `DATABRICKS_SCHEMA` — schema/database name within the catalog (default: `demo`)
- `DATABRICKS_QUERY_TIMEOUT_SECONDS` — per-query upper bound (default: `60`)

**Authentication — choose exactly one mode** (startup fails if both or neither are set, enforced by `@AssertTrue` on `DatabricksProperties`):
- **PAT mode**: `DATABRICKS_TOKEN` — personal access token. Easiest for local dev.
- **OAuth M2M mode**: `DATABRICKS_CLIENT_ID` + `DATABRICKS_CLIENT_SECRET` — service principal credentials. Preferred for production (non-human identity, rotatable, longer-lived).

### 3. Run

```bash
mvn -pl databricks-only spring-boot:run
```

```bash
curl 'http://localhost:8080/api/indices'
curl 'http://localhost:8080/api/indices?country=US&sort=symbol,asc'
curl 'http://localhost:8080/api/indices/SPX'
curl 'http://localhost:8080/actuator/health'
```

## Module-specific conventions

These supplement the cross-cutting conventions in repo-root `CLAUDE.md`.

- **JDBC URL** is constructed dynamically in `DatabricksProperties.getJdbcUrl()`. The PAT vs OAuth branch is driven by `isOAuthMode()`; the auth-mode XOR is enforced by `@AssertTrue isAuthConfigured()`.
- **Per-query timeout** via `app.databricks.query-timeout-seconds` is applied to `JdbcTemplate` in `DataSourceConfig`.
- **Resilience4j** wraps `MarketIndexRepository` methods directly with `@Retry(name = "databricks")` + `@CircuitBreaker(name = "databricks")` — no fallback method (this module has no PG to fall back to). On exhaustion, the exception propagates to `GlobalExceptionHandler` → HTTP 503.
- **Pagination** via Spring `Pageable` / `Page<T>`. Sort fields whitelisted in `MarketIndexRepository.SORTABLE_COLUMNS`; unknown sort field throws `IllegalArgumentException` → HTTP 400. Default page size is 20 (Spring Boot default).
- **HikariCP pool max size = 5** — Databricks connections are expensive; keep the pool small.

## Project structure

```
databricks-only/src/main/java/com/example/databricksaccess/
  DatabricksAccessServiceApplication.java
  config/        -> DatabricksProperties, DataSourceConfig
  model/         -> MarketIndex (Java record)
  repository/    -> MarketIndexRepository (JdbcTemplate)
  service/       -> MarketIndexService
  controller/    -> MarketIndexController
  exception/     -> MarketIndexNotFoundException, GlobalExceptionHandler
  health/        -> DatabricksHealthIndicator (Actuator @Component("databricks"))
```

## Trade-offs vs other variants

| Pick this when | Pick `databricks-pg-coexist` instead | Pick `databricks-pg-fallback` instead |
|---|---|---|
| Databricks is your only data source; no Postgres in the picture | You have distinct datasets that belong in different stores (OLTP in PG, OLAP in Databricks) | You want Databricks primary with PG as automatic failover for the same data |
| You want the simplest possible reference | You need explicit per-dataset routing decided at config time | You're migrating PG → Databricks and want safety during cutover |
