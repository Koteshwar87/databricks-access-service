# databricks-access-service

A Maven multi-module repo containing **three reference patterns** for integrating Databricks (via JDBC) into a Spring Boot application, plus **one library artifact** (`databricks-access-starter`) that packages the minimal-surface-area pattern as a real Spring Boot auto-configured starter for embedding into an existing host app.

## The three reference modules

Each is an independently runnable Spring Boot app with its own demo domain and its own README. Pick the pattern that matches your situation.

| Module | Pattern | Demo domain | Endpoint |
|---|---|---|---|
| [`databricks-only/`](databricks-only/README.md) | Single Databricks DataSource | Market indices | `GET /api/indices`, `GET /api/indices/{symbol}` |
| [`databricks-pg-coexist/`](databricks-pg-coexist/README.md) | Two DataSources in one app, **explicit per-dataset routing** via `DataLocationRegistry`, no fallback | Portfolio holdings (live=PG, historical=Databricks) | `GET /api/holdings/live`, `GET /api/holdings/historical` |
| [`databricks-pg-fallback/`](databricks-pg-fallback/README.md) | Databricks primary, **transparent PG fallback on failure** via Resilience4j `@CircuitBreaker(fallbackMethod=…)` | Trade history (same rows mirrored in both stores) | `GET /api/trades?account=…` |

## The library artifact

| Module | Shape | Purpose |
|---|---|---|
| [`databricks-access-starter/`](databricks-access-starter/README.md) | **Library JAR** (not a runnable app) | Spring Boot 3 auto-configured starter. Host app adds one Maven dep + sets `app.databricks.*` properties; gets a qualified `databricksDataSource` + `databricksJdbcTemplate` without disturbing its existing primary `DataSource`. Use this when integrating the coexist-style pattern into an existing PG-backed Spring Boot app at work. |

The `databricks-pg-coexist` and `databricks-pg-fallback` modules **consume this starter** for their Databricks connectivity (rather than hand-rolling it) — so they double as working examples of how to wire the starter into an app alongside a Postgres DataSource.

### When to pick which

- **`databricks-only`** — Databricks is your only data source. Simplest. This module also carries the most production polish (OAuth M2M auth scaffold, pagination + sort whitelist, configurable query timeout) — port those forward when adopting another pattern into a real host app.
- **`databricks-pg-coexist`** — Different datasets belong in different stores: OLTP/live rows in PG, analytical/historical scans in Databricks. Routing is explicit, config-driven, decided at startup. No automatic failover — if a dataset's configured store is down, that dataset's endpoints fail while the other dataset's keep working.
- **`databricks-pg-fallback`** — Same data lives in both stores. App always tries Databricks first; on failure, transparently falls back to PG. Useful for Strangler Fig PG→Databricks migration cutover, or production where Databricks is the primary but PG is a more-reliable backup. Caller never sees the fallback unless both stores are down (then HTTP 503).

## Tech stack (all modules)

- Java 17, Spring Boot 3.5.0, Maven multi-module
- `JdbcTemplate` + HikariCP (no JPA)
- Lombok (`@Slf4j`, `@Data`, `@RequiredArgsConstructor`); domain models are Java `record`s
- Databricks JDBC driver `com.databricks:databricks-jdbc:3.4.1` with recommended URL flags (`EnableArrow=1`, `UseNativeQuery=1`, `UserAgentEntry=…`, `ConnCatalog`, `ConnSchema`)
- Resilience4j retry + circuit breaker
- Spring Boot Actuator (custom `HealthIndicator` per DataSource)
- Postgres `16-alpine` via docker-compose (modules 2 & 3 only)
- Logging via SLF4J only — no logback config files (host owns logging)

## Quick start

```bash
# Build everything
mvn -q -DskipTests package

# Run one of the reference apps (env vars required — see the module's README)
mvn -pl databricks-only spring-boot:run
mvn -pl databricks-pg-coexist spring-boot:run
mvn -pl databricks-pg-fallback spring-boot:run

# Install the starter into your local Maven repo for use in an external host app
mvn -pl databricks-access-starter -am install
```

Each runnable module's README has the full setup: Databricks DDL/seed, docker-compose for Postgres (where applicable), env vars, and curl-based test scenarios. The starter's README has host-integration instructions.

**Cannot run all three reference apps simultaneously**: each defaults to port `8080`. Modules 2 & 3 also both bind host port `5432` for their Postgres container — start one and stop the other when switching.

## Repository layout

```
databricks-access-service/
├── pom.xml                          ← aggregator (parent)
├── README.md                        ← this file
├── CLAUDE.md                        ← cross-cutting conventions for code agents
├── databricks-only/                 ← reference variant 1 (+ README)
├── databricks-pg-coexist/           ← reference variant 2 (+ README + docker-compose.yml + init/)
├── databricks-pg-fallback/          ← reference variant 3 (+ README + docker-compose.yml + init/)
└── databricks-access-starter/       ← library JAR (+ README)
```

## Cross-module conventions

These hold across all three modules. Module-specific details (table names, env vars, ports) live in each module's own README; cross-module rules live in [`CLAUDE.md`](CLAUDE.md).

- Each module has its own Java root package (`com.example.databricksaccess` / `com.example.holdings` / `com.example.tradehistory`) and its own `*Application.java` with `@ConfigurationPropertiesScan`. Modules don't share Java code.
- All custom properties live under `app.*` (`app.databricks.*`, `app.postgres.*`, `app.holdings.routing.*`). **Never** under `spring.datasource.*` — that prefix belongs to the host's own PG config when these patterns are ported.
- Multi-DataSource modules name beans explicitly (`databricksDataSource`, `pgDataSource`, plus matching `JdbcTemplate`s); repositories inject via `@Qualifier`. No `@Primary`.
- `@RestControllerAdvice` is always package-scoped (`basePackages = "com.example.<module>"`) so handlers don't leak when embedded in a host app.
- Read-only API everywhere — Databricks side is analytical; the PG side in the reference modules is read-only too.

## What's intentionally not here

These are deferred — host-app concerns or future work, not reference-pattern concerns:

- **Tests, CI/CD, OpenAPI docs, code-quality tooling, Bean Validation on request params, BigDecimal for monetary values, correlation IDs, Micrometer metrics, `@Profile` separation.**
- **OAuth M2M auth** is scaffolded only in `databricks-only/`. Modules 2 & 3 use PAT only — port the OAuth path when needed.
- **Pagination + sort whitelist** is only in `databricks-only/`. Port to modules 2 & 3 when their endpoints need it.
- **Data sync** between Databricks and PG for `databricks-pg-fallback/` — demo manually seeds both with identical rows; production setups would use CDC, event-driven sync, or dual-writes.
