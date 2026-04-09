# databricks-access-service

## What is this?
A Spring Boot REST API that connects to Databricks via JDBC. Uses a stock market indices domain as a sample.

## Tech Stack
- Java 17, Spring Boot 3.5.0, Maven
- JdbcTemplate + HikariCP (no JPA)
- Lombok (`@Slf4j`, `@Data`, `@RequiredArgsConstructor`)
- Logback with `logback-spring.xml` (console + rolling file to `logs/`)
- Databricks JDBC driver (`com.databricks:databricks-jdbc:2.6.40`)

## Project Structure
```
src/main/java/com/example/databricksaccess/
  config/        -> DatabricksProperties, DataSourceConfig
  model/         -> MarketIndex (Java record)
  repository/    -> MarketIndexRepository (JdbcTemplate)
  service/       -> MarketIndexService
  controller/    -> MarketIndexController, HealthController
  exception/     -> MarketIndexNotFoundException, GlobalExceptionHandler
```

## API Endpoints
- `GET /api/indices` — list all (optional `?country=` filter)
- `GET /api/indices/{symbol}` — get by symbol (e.g. SPX, NSEI)
- `GET /health/databricks` — connectivity check

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
- Logback config: console + rolling file appender (`logs/` dir, 10MB max per file, 30 days retention)
