package com.example.holdings.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("databricks")
public class DatabricksHealthIndicator extends AbstractHealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public DatabricksHealthIndicator(@Qualifier("databricksJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        long start = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            builder.up().withDetail("responseTimeMs", System.currentTimeMillis() - start);
        } catch (Exception e) {
            builder.down(e).withDetail("responseTimeMs", System.currentTimeMillis() - start);
        }
    }
}
