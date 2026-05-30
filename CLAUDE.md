# databricks-access-service

## What is this?
A Spring Boot REST API that connects to Databricks via JDBC. Uses a stock market indices domain as a sample.

## Tech Stack
- Java 17, Spring Boot 3.5.0, Maven
- JdbcTemplate + HikariCP (no JPA)
- Lombok (`@Slf4j`, `@Data`, `@RequiredArgsConstructor`)
- Logging via SLF4J (`@Slf4j`) — no logging config in this module; host app owns logback/CloudWatch when embedded, Spring Boot defaults apply for standalone local runs
- Databricks JDBC driver (`com.databricks:databricks-jdbc:3.4.1`)
- Spring Boot Actuator (health endpoint)
- Resilience4j (retry + circuit breaker around repository calls)

## Project Structure
```
src/main/java/com/example/databricksaccess/
  config/        -> DatabricksProperties, DataSourceConfig
  model/         -> MarketIndex (Java record)
  repository/    -> MarketIndexRepository (JdbcTemplate)
  service/       -> MarketIndexService
  controller/    -> MarketIndexController
  exception/     -> MarketIndexNotFoundException, GlobalExceptionHandler
  health/        -> DatabricksHealthIndicator (Actuator)
```

## API Endpoints
- `GET /api/indices` — list all (optional `?country=` filter)
- `GET /api/indices/{symbol}` — get by symbol (e.g. SPX, NSEI)
- `GET /actuator/health` — overall health (HTTP 503 when any component is DOWN)
- `GET /actuator/health/databricks` — Databricks-only component with `responseTimeMs` detail
- `GET /actuator/circuitbreakers` — state of all circuit breakers
- `GET /actuator/circuitbreakerevents` — recent circuit-breaker transitions

## Configuration
Environment variables required:
- `DATABRICKS_HOST` — server hostname
- `DATABRICKS_HTTP_PATH` — SQL warehouse HTTP path
- `DATABRICKS_TOKEN` — personal access token

## Build & Run
```bash
mvn compile                # compile
mvn spring-boot:run        # run (requires env vars set)
```

## Key Conventions
- Read-only API (no writes — Databricks is analytical)
- `@ConfigurationProperties` for config binding (not @Value)
- JDBC URL constructed in `DatabricksProperties.getJdbcUrl()`
- Schema name configurable via `app.databricks.schema` (default: demo)
- Global exception handler returns clean JSON errors
- HikariCP pool max size = 5 (Databricks connections are expensive)
- Use Lombok `@Slf4j` for logging (not manual LoggerFactory)
- Use Lombok `@RequiredArgsConstructor` for constructor injection (no manual constructors)
- Use Lombok `@Data` for config POJOs
- Resilience4j wraps `MarketIndexRepository` methods with `@Retry(name = "databricks")` + `@CircuitBreaker(name = "databricks")` — config under `resilience4j.*` in application.yml
