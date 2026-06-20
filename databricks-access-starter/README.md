# databricks-access-starter

A Spring Boot 3 **auto-configured starter library** that adds a qualified Databricks `DataSource` and `JdbcTemplate` to a host application — without touching the host's existing primary DataSource (typically Postgres via `spring.datasource.*`).

Unlike the three reference modules in this repo (`databricks-only/`, `databricks-pg-coexist/`, `databricks-pg-fallback/`) which are **runnable demo apps**, this module is a **library JAR** designed to be embedded into another Spring Boot application.

## What you get

When the host adds this dependency and sets `app.databricks.enabled=true`, the starter contributes:

| Bean | Type | Qualifier name | Notes |
|---|---|---|---|
| `databricksDataSource` | `javax.sql.DataSource` (Hikari) | `databricksDataSource` | Declared with `@Bean(defaultCandidate = false)` — invisible to unqualified `@Autowired DataSource`; only resolves when explicitly qualified |
| `databricksJdbcTemplate` | `org.springframework.jdbc.core.JdbcTemplate` | `databricksJdbcTemplate` | Same — `@Bean(defaultCandidate = false)`, qualifier-only. Wraps `databricksDataSource`; per-query timeout applied |
| `databricksJdbcClient` | `org.springframework.jdbc.core.simple.JdbcClient` | `databricksJdbcClient` | Same — `@Bean(defaultCandidate = false)`, qualifier-only. Fluent Spring 6.1+ wrapper over `databricksJdbcTemplate`; recommended for new repository code |
| `databricks` | `org.springframework.boot.actuate.health.HealthIndicator` | `databricks` | Only created when Spring Boot Actuator is on the classpath; surfaces at `/actuator/health/databricks` |

That's it. No controllers, no repositories, no opinions about your domain model.

## Why this won't break your existing PG setup

| Spring Boot stage | What happens | Why it's safe |
|---|---|---|
| `DataSourceAutoConfiguration` runs | Creates the host's `dataSource` bean from `spring.datasource.*`. Marks it `@Primary`. | The starter sets no `spring.datasource.*` properties. |
| `DatabricksAutoConfiguration` runs | Creates `databricksDataSource` with `@Bean(defaultCandidate = false)`. | The bean is excluded from default autowiring candidacy — Spring will never even consider it for an unqualified `@Autowired DataSource`. No ambiguity with the host's PG bean, regardless of whether the host's bean is `@Primary` or not. |
| `JdbcTemplateAutoConfiguration` runs | Creates the host's default `JdbcTemplate` against the primary DataSource (PG). | Our `databricksJdbcTemplate` is also `defaultCandidate = false` — same protection. |
| Host's existing `@Autowired DataSource ds` | Resolves to PG. | Unchanged. Spring sees our bean as a non-default candidate and skips it; PG is the only one in the running. |
| Host's existing `@Autowired JdbcTemplate jdbc` | Resolves to the PG-backed template. | Unchanged. |
| Host's new `@Autowired @Qualifier("databricksJdbcTemplate") JdbcTemplate jdbc` | Resolves to the Databricks-backed template. | Qualifier-based injection bypasses the `defaultCandidate` filter. New capability; purely additive. |

## Host integration

### 1. Add the dependency

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>databricks-access-starter</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

The starter brings in `spring-boot-starter-jdbc` (which includes HikariCP) and `databricks-jdbc:3.4.1` at runtime. Spring Boot Actuator is an **optional** dependency — declare it in the host explicitly if you want the `/actuator/health/databricks` indicator to be active.

### 2. Set properties

In the host's `application.yml`:

```yaml
app:
  databricks:
    enabled: true
    hostname: ${DATABRICKS_HOST}              # e.g. dbc-xxxxxxxx-xxxx.cloud.databricks.com
    http-path: ${DATABRICKS_HTTP_PATH}        # e.g. /sql/1.0/warehouses/abc123
    catalog: workspace
    schema: demo
    query-timeout-seconds: 60
    client-id: ${DATABRICKS_CLIENT_ID}        # service-principal application id
    client-secret: ${DATABRICKS_CLIENT_SECRET}  # service-principal secret (from your secrets manager)
    # hikari: <override any default below if needed>
```

The starter ships production-tuned Hikari defaults — you don't need to specify the `hikari:` block unless you want to override something. See the configuration reference below for what's set out of the box.

Authentication is **OAuth M2M** (Machine-to-Machine) with a Databricks service principal. The starter wires `AuthMech=11;Auth_Flow=1;OAuth2ClientId=…;OAuth2Secret=…` into the JDBC URL; the driver fetches and refreshes the short-lived bearer token automatically — your application code never sees or handles tokens.

The starter is opt-in via `app.databricks.enabled=true`. If that property is unset or false, the starter does nothing — the dependency is safe to add even before the team is ready to wire it in. Once enabled, `client-id` and `client-secret` are required (validated as `@NotBlank` at startup).

#### Where the property values come from

The starter reads only typed Spring `@ConfigurationProperties`. It does **not** read env vars directly. The host populates the properties from wherever its config infrastructure lives:

- Plain env vars (`${DATABRICKS_HOST}` resolves via `Environment`)
- AWS Secrets Manager (e.g. `spring-cloud-aws-secrets-manager`)
- AWS Systems Manager Parameter Store
- HashiCorp Vault
- A Kubernetes `ConfigMap` / `Secret`

Anything that becomes a Spring `Environment` source works.

### 3. Inject the qualified bean — `JdbcClient` (recommended) or `JdbcTemplate`

**Recommended: `JdbcClient`** (Spring Framework 6.1+, fluent API):

```java
@Repository
@RequiredArgsConstructor
public class MarketDataRepository {

    @Qualifier("databricksJdbcClient")
    private final JdbcClient databricks;

    public List<MarketIndex> findByCountry(String country) {
        return databricks.sql("""
                SELECT symbol, name, country, value, currency
                FROM market_indices
                WHERE country = :country
                """)
            .param("country", country)
            .query(MarketIndex.class)
            .list();
    }
}
```

**Or, classic `JdbcTemplate`** if you prefer (same underlying execution path, both go through the same Hikari pool and respect the same query timeout):

```java
@Repository
@RequiredArgsConstructor
public class MarketDataRepository {

    @Qualifier("databricksJdbcTemplate")
    private final JdbcTemplate databricksJdbc;

    public List<MarketIndex> findAll() {
        return databricksJdbc.query(
            "SELECT symbol, name, country, value, currency FROM market_indices",
            (rs, i) -> new MarketIndex(
                rs.getString("symbol"),
                rs.getString("name"),
                rs.getString("country"),
                rs.getObject("value", Double.class),
                rs.getString("currency")));
    }
}
```

The repository is yours to write — the starter just exposes the qualified beans.

### 4. (Optional) Verify the new health component

If you have Spring Boot Actuator enabled in the host:

```bash
curl http://localhost:8080/actuator/health/databricks
# {"status":"UP","details":{"responseTimeMs":42}}
```

The existing `/actuator/health/db` component (your PG indicator) is unchanged.

## Configuration reference

| Property | Type | Default | Notes |
|---|---|---|---|
| `app.databricks.enabled` | boolean | `false` | Master switch. Must be `true` for any starter beans to be registered. |
| `app.databricks.hostname` | String | (required) | Databricks workspace hostname. |
| `app.databricks.http-path` | String | (required) | SQL warehouse HTTP path. |
| `app.databricks.catalog` | String | (required) | Unity Catalog catalog name. |
| `app.databricks.schema` | String | (required) | Schema/database name within the catalog. |
| `app.databricks.query-timeout-seconds` | int | `60` | Per-query upper bound; applied to `JdbcTemplate`. |
| `app.databricks.client-id` | String | (required) | OAuth M2M service-principal application id. |
| `app.databricks.client-secret` | String | (required) | OAuth M2M service-principal secret. Source from your secrets manager. |
| `app.databricks.hikari.maximum-pool-size` | int | `5` | Databricks connections are expensive; keep small. |
| `app.databricks.hikari.minimum-idle` | int | `1` | |
| `app.databricks.hikari.connection-timeout` | long (ms) | `60000` | Accommodates Databricks SQL-warehouse cold-start (30–60s). |
| `app.databricks.hikari.idle-timeout` | long (ms) | `120000` | Aggressive idle eviction keeps the pool lean. |
| `app.databricks.hikari.max-lifetime` | long (ms) | `3300000` | **Critical**: just under the 60-min OAuth M2M token lifetime, so connections are recycled before their bearer token can expire mid-use. Do not raise above ~3500000. |
| `app.databricks.hikari.auto-commit` | boolean | `false` | Databricks has no real transactions; disabling avoids per-borrow toggle round trips. |
| `app.databricks.hikari.connection-test-query` | String | `SELECT 1` | Fallback if the driver's `Connection.isValid()` is broken or slow. |
| `app.databricks.hikari.validation-timeout` | long (ms) | `10000` | How long the validation query is allowed to run. |
| `app.databricks.hikari.keepalive-time` | long (ms) | `180000` | Pings idle connections every 3 min so intermediate firewalls / load balancers don't kill them. |
| `app.databricks.hikari.leak-detection-threshold` | long (ms) | `60000` | Logs a warning if a connection isn't returned within this window — catches connection-leak bugs. |

Note: `app.databricks.hikari.*` is intentionally **not** under `spring.datasource.hikari.*` — that namespace belongs to the host's primary PG pool, which the starter never touches.

## Overriding a starter bean

Every bean the starter declares uses `@ConditionalOnMissingBean(name = "<beanName>")`. To override, define a bean of the same name in any `@Configuration` class in the host:

```java
@Configuration
public class CustomDatabricksConfig {
    @Bean(name = "databricksDataSource")
    public DataSource databricksDataSource(/* your params */) {
        // your custom DataSource
    }
}
```

The starter then steps aside for that bean.

## Recommended add-on: Resilience4j

The starter intentionally does **not** ship retry/circuit-breaker config — Resilience4j is a host-wide concern (your other downstreams use it too), and the right thresholds depend on your team's SLA. But there's a recommended baseline tuned for Databricks's failure modes (warehouse cold-starts, transient SQL errors), and you should adopt something close to it.

### 1. Add the dependency to your host

```xml
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot3</artifactId>
  <version>2.2.0</version>
</dependency>
```

### 2. Add this `resilience4j` block to the host's `application.yml`

```yaml
resilience4j:
  retry:
    instances:
      databricks:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - org.springframework.dao.DataAccessException
          - java.sql.SQLTransientException
  circuitbreaker:
    instances:
      databricks:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
```

What this gives you:

- **Retry**: up to 3 attempts on transient SQL errors, with 1s / 2s / 4s exponential backoff. Permanent errors (`PERMISSION_DENIED`, SQL syntax, etc.) propagate immediately — only `DataAccessException` and `SQLTransientException` are retried.
- **Circuit breaker**: opens after 5+ failures in any 10-call window with ≥50% failure rate. Stays open 30s, then half-opens to admit 3 probe calls. If they succeed, closes; otherwise re-opens. Prevents retry storms against a known-dead warehouse.

### 3. Annotate your Databricks-backed methods

```java
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final MarketDataRepository repo;

    @Retry(name = "databricks")
    @CircuitBreaker(name = "databricks")
    public List<MarketIndex> getAll() {
        return repo.findAll();
    }
}
```

### Optional: fallback method (`@CircuitBreaker(fallbackMethod = "...")`)

If you have a fallback data source (e.g. a Postgres mirror) and want the failure to be invisible to the caller, declare a fallback method in the same class with the original signature plus a trailing `Throwable`:

```java
@Retry(name = "databricks")
@CircuitBreaker(name = "databricks", fallbackMethod = "getFromPg")
public List<MarketIndex> getAll() {
    return repo.findAll();
}

private List<MarketIndex> getFromPg(Throwable cause) {
    log.warn("Databricks failed, falling back to PG: {}", cause.getMessage());
    return pgRepo.findAll();
}
```

> **Important**: declare `fallbackMethod` only on `@CircuitBreaker` (the outer decorator), **not** on `@Retry`. Otherwise the retry's fallback fires before the circuit breaker ever sees the failure, and the CB never opens.

See `databricks-pg-fallback/` in this repo for a full working example of the fallback pattern.

## What the starter intentionally does NOT include

- **REST controllers** — the host owns its REST namespace.
- **Repositories or domain models** — the host owns its data shapes.
- **`application.yml` or `application.properties`** — the host owns its config.
- **`@SpringBootApplication`** — this is a library, not an app.
- **`spring-boot-maven-plugin`** — produces a plain jar, not an executable jar.
- **`@Primary` anywhere** — never overrides the host's primary DataSource.
- **Resilience4j wrapping** — recommended add-on, not bundled (see above).
- **`DataLocationRegistry`** routing — see `databricks-pg-coexist/` for that pattern; the starter is minimal-surface-area by design. Easy to layer on later if needed.

## Local install for testing in an out-of-tree host

```bash
mvn -pl databricks-access-starter -am install
```

Then add the dependency in the host's `pom.xml` as shown above. The starter will be picked up from the local Maven repository (`~/.m2/repository`).
