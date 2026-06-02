# databricks-access-service

A Maven multi-module reference repo demonstrating three patterns for integrating Databricks (via JDBC) into a Spring Boot app. The user will pick one of these patterns to adapt into a production host application that already runs Postgres-backed services with CloudWatch logging.

## The three reference modules

| Module | Pattern | Demo domain | Endpoint | Status |
|---|---|---|---|---|
| `databricks-only/` | Single Databricks DataSource | Market indices | `GET /api/indices`, `GET /api/indices/{symbol}` | ✅ merged |
| `databricks-pg-coexist/` | Two DataSources in one app, **explicit per-dataset routing** via `DataLocationRegistry`, **no fallback** | Portfolio holdings (live=PG, historical=Databricks) | `GET /api/holdings/live`, `GET /api/holdings/historical` | ✅ merged |
| `databricks-pg-fallback/` | Databricks primary, **implicit PG fallback on failure** via Resilience4j `@CircuitBreaker(fallbackMethod=…)` | Trade history (same rows mirrored in both stores) | `GET /api/trades?account=…` | ✅ merged (PR #9) |

Each module has its own README with full setup, env vars, Docker compose (where applicable), Databricks DDL/seed, and curl-based test scenarios. **Always read the target module's README before making changes there** — module-specific details (port numbers, container names, table names) live in the README, not here.

### When to pick which

- **`databricks-only`** — host has nothing else; you're fully on Databricks. Simplest.
- **`databricks-pg-coexist`** — each dataset has a natural home (OLTP rows in PG, analytical scans in Databricks). Routing is explicit and known at config time. No automatic failover.
- **`databricks-pg-fallback`** — Strangler Fig migration cutover *or* Databricks is your primary but PG is your reliable backup with the same data. Failure is invisible to the caller.

## Common tech stack (all modules)

- Java 17, Spring Boot 3.5.0, Maven multi-module
- `JdbcTemplate` + HikariCP (no JPA)
- Lombok (`@Slf4j`, `@Data`, `@RequiredArgsConstructor`)
- Databricks JDBC driver `com.databricks:databricks-jdbc:3.4.1` — driver class is still `com.databricks.client.jdbc.Driver` (not renamed in v3)
- Recommended URL flags: `EnableArrow=1`, `UseNativeQuery=1`, `UserAgentEntry=<module-name>`, `ConnCatalog`, `ConnSchema`
- Resilience4j retry + circuit breaker (config under `resilience4j.*` in `application.yml`)
- Spring Boot Actuator (`/actuator/health`, `/actuator/circuitbreakers`, etc.)
- Logging via SLF4J only — **no logback config files in any module** (host owns logging; Spring Boot defaults apply for local runs)
- `@ConfigurationProperties` (not `@Value`); `@ConfigurationPropertiesScan` on each `*Application` class

## Repository layout

```
databricks-access-service/
├── pom.xml                       ← aggregator (parent)
├── CLAUDE.md                     ← this file
├── springboot-databricks-base-readme.md
├── databricks-only/              ← variant 1
├── databricks-pg-coexist/        ← variant 2
└── databricks-pg-fallback/       ← variant 3
```

## Build & run

```bash
mvn -q -DskipTests package                       # build all three modules
mvn -pl <module> -am compile                     # compile one module + deps
mvn -pl <module> spring-boot:run                 # run one module (needs env vars; see module README)
```

Each module is independently runnable. They cannot all run simultaneously without changing server ports (each defaults to `8080`); `databricks-pg-coexist` and `databricks-pg-fallback` also can't run their Postgres containers at the same time (both bind host `5432` — start one, stop the other).

## Multi-module conventions

These hold across all three modules. Module-specific details (table names, env vars beyond the standard set, port numbers) belong in the module's own README.

### Naming
- Each module has its own root Java package: `com.example.databricksaccess`, `com.example.holdings`, `com.example.tradehistory`. Never share Java packages across modules.
- Each module's `*Application.java` is annotated with `@ConfigurationPropertiesScan`.
- Multi-DataSource modules name their beans explicitly: `databricksDataSource` / `databricksJdbcTemplate`, `pgDataSource` / `pgJdbcTemplate`. Repositories inject via `@Qualifier(...)`.

### Config namespace
- All custom properties live under `app.*` (e.g., `app.databricks.*`, `app.postgres.*`, `app.holdings.routing.*`). **Never** under `spring.datasource.*` — that steals config from the host app when embedded.
- Hikari per DataSource: `app.databricks.hikari.*`, `app.postgres.hikari.*` (each `DataSourceConfig` `@Bean` binds with `@ConfigurationProperties("app.<x>.hikari")`).

### Cross-cutting Spring
- `@RestControllerAdvice` is **always** package-scoped (`basePackages = "com.example.<module>"`) so handlers don't leak into the host app.
- Health indicators are `@Component("<name>")` so they surface as `/actuator/health/<name>`.
- Read-only API everywhere (Databricks side is analytical; PG side is also read-only in these demos).

### Resilience4j
- `@Retry` + `@CircuitBreaker` named `"databricks"` (always).
- In the fallback module specifically, `fallbackMethod` lives **only** on `@CircuitBreaker`, not on `@Retry`, and points at a method in the same class with `(originalParams..., Throwable cause)` signature.

### Postgres for multi-DataSource modules
- Each module has its own `docker-compose.yml` with `postgres:16-alpine` and an `init/` dir mounted at `/docker-entrypoint-initdb.d:ro`.
- Init scripts: `01-schema.sql`, `02-seed.sql`. Schema script uses `CREATE TABLE IF NOT EXISTS`; seed script is plain `INSERT`.
- Container names differ per module (`holdings-pg`, `tradehistory-pg`) but host port is `5432` for both — see the warning above.

### Lombok
- `@Slf4j` for logging (never `LoggerFactory.getLogger(...)`).
- `@RequiredArgsConstructor` for DI (no hand-written constructors).
- `@Data` only for config POJOs / properties classes.
- Domain models are Java `record`s, not Lombok `@Data` classes.

### Databricks JDBC specifics
- JDBC URL is constructed in `DatabricksProperties.getJdbcUrl()`. Catalog and schema are passed as `ConnCatalog=…;ConnSchema=…` URL params; SQL in repositories is **unqualified** (just `FROM trades`, not `FROM workspace.demo.trades`).
- Per-query timeout via `app.databricks.query-timeout-seconds` (where present), applied to `JdbcTemplate`.
- `RowMapper`s use `rs.getObject(col, Type.class)` for null-safe reads (JDBC 4.2+).

### PR workflow (mandatory)
Every change: plan mode → branch off `main` → commit with Co-Authored-By → push → provide PR URL → wait for the user to merge from GitHub UI. `gh` CLI is not installed locally, so the PR is opened by the user via the URL link. Never commit to `main` directly. Don't bundle unrelated concerns in one PR.

## What's intentionally not here

The reference modules deliberately skip these — they're host-app concerns or future work:

- **Tests** — user explicitly deferred.
- **CI/CD, code quality tooling, OpenAPI docs, Bean Validation on request params, BigDecimal for money, correlation IDs, Micrometer metrics, `@Profile` separation.**
- **Data sync** between Databricks and PG for the fallback module — demo manually seeds both with identical rows; production setups would use CDC, event-driven sync, or dual-writes.
- **OAuth M2M auth** is scaffolded only in `databricks-only/`. The other two modules use PAT only — port the OAuth path from `databricks-only/` when needed.
- **Pagination + sort whitelist** only in `databricks-only/`. The other two modules use simple list endpoints — port when needed.
- **Top-level repo README** comparing all three variants side by side — natural next step but not done yet.
