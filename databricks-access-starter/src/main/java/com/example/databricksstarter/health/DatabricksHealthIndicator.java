package com.example.databricksstarter.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class DatabricksHealthIndicator extends AbstractHealthIndicator {

    private final JdbcTemplate jdbcTemplate;

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
