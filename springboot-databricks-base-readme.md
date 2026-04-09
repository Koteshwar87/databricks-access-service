# databricks-access-service

A clean, production-standard Spring Boot base project to connect to Databricks.
No domain logic. No over-engineering. Just a solid foundation.

---

## Goal

Provide a **minimal but production-quality base setup** for:

- Spring Boot → Databricks connection
- JDBC-based access
- Clean structure
- Proper configuration
- Ready to extend

---

## Tech Stack

- Java 21
- Spring Boot 3.5+
- Maven
- JdbcTemplate
- HikariCP
- SLF4J logging

---

## What this project is (and is not)

### This IS:
- Base connectivity project
- Clean production-ready structure
- Proper config handling
- Good practices from day one

### This is NOT:
- Full product
- Domain-specific implementation
- Over-architected system

---

## Databricks Setup (Minimal Required)

### 1. Create account
Create Databricks account (Free Edition is fine)

### 2. Create SQL Warehouse
- Go to SQL Warehouses
- Create warehouse (use default/free)

### 3. Create test table

```sql
CREATE SCHEMA IF NOT EXISTS demo;

CREATE TABLE demo.test_data (
    id INT,
    name STRING
);

INSERT INTO demo.test_data VALUES (1, 'test');
```

### 4. Get connection details

From Databricks UI:

- Server Hostname
- HTTP Path
- Token (or auth method)

---

## Project Structure

```
databricks-access-service
│
├── src/main/java/com/example/databricks
│   ├── config
│   │   └── DataSourceConfig.java
│   ├── repository
│   │   └── TestRepository.java
│   ├── service
│   │   └── TestService.java
│   ├── controller
│   │   └── TestController.java
│   └── Application.java
│
└── resources
    └── application.yml
```

---

## application.yml

```yaml
app:
  databricks:
    hostname: ${DATABRICKS_HOST}
    http-path: ${DATABRICKS_HTTP_PATH}
    token: ${DATABRICKS_TOKEN}

spring:
  datasource:
    driver-class-name: com.databricks.client.jdbc.Driver
```

---

## JDBC URL

Construct in config:

```
jdbc:databricks://<host>:443/default;httpPath=<path>;AuthMech=3;UID=token;PWD=<token>;
```

---

## DataSourceConfig (concept)

- Build JDBC URL
- Configure HikariCP
- Expose JdbcTemplate

---

## Repository

- Use JdbcTemplate
- Write plain SQL

Example:

```
SELECT id, name FROM demo.test_data
```

---

## Controller

Expose simple endpoint:

```
GET /test
```

Returns data from Databricks.

---

## Production Practices Included

Even though this is a base project:

- No hardcoded secrets
- Externalized config
- Clean layering (controller/service/repo)
- Connection pooling
- Logging ready
- Easily extensible

---

## Not Included (intentionally)

- JPA
- Complex architecture
- Domain logic
- Multiple datasources
- Security frameworks

Keep base simple and correct.

---

## Next Steps (after this)

1. Add proper exception handling
2. Add health check
3. Add metrics
4. Add auth/security if needed
5. Extend repositories

---

## Key Principle

**Start simple, but not sloppy.**

This project gives you:
- Clean base
- Correct approach
- No future rewrite needed

