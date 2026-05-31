# databricks-access-service

> **Repo layout**: this is a Maven multi-module project. The active app lives in `databricks-only/`. Future reference variants (`databricks-pg-coexist/`, `databricks-pg-fallback/`) will be added as sibling modules — each demonstrates a different pattern for combining Databricks with another data source. Commands shown below target the `databricks-only` module specifically.

## What is this?
A Spring Boot REST API that connects to Databricks via JDBC. Uses a stock market indices domain as a sample.

## Tech Stack
- Java 17, Spring Boot 3.5.0, Maven
- JdbcTemplate + HikariCP (no JPA)
- Lombok (`@Slf4j`, `@Data`, `@RequiredArgsConstructor`)
- Logging via SLF4J (`@Slf4j`) — no logging config in this module; host app owns logback/CloudWatch when embedded, Spring Boot defaults apply for standalone local runs
- Databricks JDBC driver (`com.databricks:databricks-jdbc:3.4.1`) with recommended URL flags: `EnableArrow=1`, `UseNativeQuery=1`, `UserAgentEntry=databricks-access-service`, `ConnCatalog`, `ConnSchema`
- Spring Boot Actuator (health endpoint)
- Resilience4j (retry + circuit breaker around repository calls)

## Project Structure
```
databricks-only/src/main/java/com/example/databricksaccess/
  config/        -> DatabricksProperties, DataSourceConfig
  model/         -> MarketIndex (Java record)
  repository/    -> MarketIndexRepository (JdbcTemplate)
  service/       -> MarketIndexService
  controller/    -> MarketIndexController
  exception/     -> MarketIndexNotFoundException, GlobalExceptionHandler
  health/        -> DatabricksHealthIndicator (Actuator)
```

## API Endpoints
- `GET /api/indices` — paginated list (Spring `Page` envelope); supports `?page=`, `?size=` (default 20), `?sort=field,dir`, optional `?country=` filter
- `GET /api/indices/{symbol}` — single result, no pagination
- `GET /actuator/health` — overall health (HTTP 503 when any component is DOWN)
- `GET /actuator/health/databricks` — Databricks-only component with `responseTimeMs` detail
- `GET /actuator/circuitbreakers` — state of all circuit breakers
- `GET /actuator/circuitbreakerevents` — recent circuit-breaker transitions

## Configuration
Required environment variables:
- `DATABRICKS_HOST` — server hostname
- `DATABRICKS_HTTP_PATH` — SQL warehouse HTTP path

Optional environment variables (defaults shown):
- `DATABRICKS_CATALOG` — Unity Catalog catalog name (default: `workspace`)
- `DATABRICKS_SCHEMA` — schema/database name within the catalog (default: `demo`)
- `DATABRICKS_QUERY_TIMEOUT_SECONDS` — per-query upper bound (default: `60`)

Authentication (choose exactly one mode; startup fails if both or neither are set):
- **PAT mode**: `DATABRICKS_TOKEN` — personal access token
- **OAuth M2M mode**: `DATABRICKS_CLIENT_ID` + `DATABRICKS_CLIENT_SECRET` — service principal credentials (preferred for production; non-human identity, rotatable, longer-lived)

## Build & Run
```bash
mvn -pl databricks-only compile          # compile the databricks-only module
mvn -pl databricks-only spring-boot:run  # run the databricks-only module (requires env vars set)
```

## Key Conventions
- Read-only API (no writes — Databricks is analytical)
- `@ConfigurationProperties` for config binding (not @Value)
- JDBC URL constructed in `DatabricksProperties.getJdbcUrl()`
- Catalog + schema separated via `app.databricks.catalog` / `app.databricks.schema` (passed to JDBC as `ConnCatalog` / `ConnSchema`); SQL in the repository is unqualified
- Per-query timeout via `app.databricks.query-timeout-seconds` applied to `JdbcTemplate`
- Global exception handler returns clean JSON errors
- HikariCP pool max size = 5 (Databricks connections are expensive)
- Use Lombok `@Slf4j` for logging (not manual LoggerFactory)
- Use Lombok `@RequiredArgsConstructor` for constructor injection (no manual constructors)
- Use Lombok `@Data` for config POJOs
- Resilience4j wraps `MarketIndexRepository` methods with `@Retry(name = "databricks")` + `@CircuitBreaker(name = "databricks")` — config under `resilience4j.*` in application.yml
- Pagination via Spring `Pageable` / `Page<T>` on list endpoints; default page size 20 (Spring Boot default)
- Sort fields whitelisted in `MarketIndexRepository.SORTABLE_COLUMNS`; unknown sort field → HTTP 400
