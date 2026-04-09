package com.example.databricksaccess.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/databricks")
    public Map<String, Object> checkDatabricks() {
        long start = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long elapsed = System.currentTimeMillis() - start;
            return Map.of("status", "UP", "responseTimeMs", elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return Map.of("status", "DOWN", "responseTimeMs", elapsed, "error", e.getMessage());
        }
    }
}
