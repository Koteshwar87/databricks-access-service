# databricks-access-starter

A Spring Boot 3 **auto-configured starter library** that adds a qualified Databricks `DataSource` and `JdbcTemplate` to a host application — without touching the host's existing primary DataSource (typically Postgres via `spring.datasource.*`).

Unlike the three reference modules in this repo (`databricks-only/`, `databricks-pg-coexist/`, `databricks-pg-fallback/`) which are **runnable demo apps**, this module is a **library JAR** designed to be embedded into another Spring Boot application.

## What you get

When the host adds this dependency and sets `app.databricks.enabled=true`, the starter contributes:

| Bean | Type | Qualifier name | Notes |
|---|---|---|---|
| `databricksDataSource` | `javax.sql.DataSource` (Hikari) | `databricksDataSource` | **Not `@Primary`** — host's existing DataSource remains primary |
| `databricksJdbcTemplate` | `org.springframework.jdbc.core.JdbcTemplate` | `databricksJdbcTemplate` | Wraps `databricksDataSource`; per-query timeout applied |
| `databricks` | `org.springframework.boot.actuate.health.HealthIndicator` | `databricks` | Only created when Spring Boot Actuator is on the classpath; surfaces at `/actuator/health/databricks` |

That's it. No controllers, no repositories, no opinions about your domain model.

## Why this won't break your existing PG setup

| Spring Boot stage | What happens | Why it's safe |
|---|---|---|
| `DataSourceAutoConfiguration` runs | Creates the host's `dataSource` bean from `spring.datasource.*`. Marks it `@Primary`. | The starter sets no `spring.datasource.*` properties. |
| `DatabricksAutoConfiguration` runs | Creates `databricksDataSource` (not `@Primary`). | Multiple `DataSource` beans are legal as long as one is primary — and the host's PG one is. |
| `JdbcTemplateAutoConfiguration` runs | Creates the host's default `JdbcTemplate` against the primary DataSource (PG). | The starter creates `databricksJdbcTemplate` separately; different bean name, no collision. |
| Host's existing `@Autowired DataSource ds` | Resolves to PG. | Unchanged. |
| Host's existing `@Autowired JdbcTemplate jdbc` | Resolves to the PG-backed template. | Unchanged. |
| Host's new `@Autowired @Qualifier("databricksJdbcTemplate") JdbcTemplate jdbc` | Resolves to the Databricks-backed template. | New capability; purely additive. |

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

    # ONE of these two auth modes (XOR — both or neither will fail startup):
    token: ${DATABRICKS_TOKEN}
    # OR:
    # client-id: ${DATABRICKS_CLIENT_ID}
    # client-secret: ${DATABRICKS_CLIENT_SECRET}

    hikari:
      maximum-pool-size: 5
      minimum-idle: 1
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

The starter is opt-in via `app.databricks.enabled=true`. If that property is unset or false, the starter does nothing — the dependency is safe to add even before the team is ready to wire it in.

#### Where the property values come from

The starter reads only typed Spring `@ConfigurationProperties`. It does **not** read env vars directly. The host populates the properties from wherever its config infrastructure lives:

- Plain env vars (`${DATABRICKS_HOST}` resolves via `Environment`)
- AWS Secrets Manager (e.g. `spring-cloud-aws-secrets-manager`)
- AWS Systems Manager Parameter Store
- HashiCorp Vault
- A Kubernetes `ConfigMap` / `Secret`

Anything that becomes a Spring `Environment` source works.

### 3. Inject the qualified `JdbcTemplate`

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

The repository is yours to write — the starter just exposes the `JdbcTemplate`.

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
| `app.databricks.token` | String | — | PAT (personal access token). Mutually exclusive with `client-id`/`client-secret`. |
| `app.databricks.client-id` | String | — | OAuth M2M service-principal client id. Requires `client-secret`. |
| `app.databricks.client-secret` | String | — | OAuth M2M service-principal client secret. Requires `client-id`. |
| `app.databricks.hikari.maximum-pool-size` | int | `5` | Databricks connections are expensive; keep small. |
| `app.databricks.hikari.minimum-idle` | int | `1` | |
| `app.databricks.hikari.connection-timeout` | long (ms) | `30000` | |
| `app.databricks.hikari.idle-timeout` | long (ms) | `600000` | |
| `app.databricks.hikari.max-lifetime` | long (ms) | `1800000` | |

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

The starter intentionally does **not** ship retry/circuit-breaker wrapping — different hosts have different resiliency conventions. For production use, the recommended pattern is:

1. Add `io.github.resilience4j:resilience4j-spring-boot3` to the host
2. Annotate the host's Databricks-backed repository methods with `@Retry(name = "databricks")` and `@CircuitBreaker(name = "databricks")`
3. Configure under `resilience4j.*` in the host's `application.yml`

See `databricks-pg-fallback/README.md` and the source under `databricks-pg-fallback/src/main/java/.../service/TradeHistoryService.java` for a working example of this pattern (including a fallback method to a secondary store).

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
