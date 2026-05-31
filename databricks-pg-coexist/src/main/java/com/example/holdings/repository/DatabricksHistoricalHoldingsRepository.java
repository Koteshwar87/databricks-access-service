package com.example.holdings.repository;

import com.example.holdings.model.Holding;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class DatabricksHistoricalHoldingsRepository {

    private static final String INSTANCE = "databricks";
    private static final String COLUMNS = "id, account, symbol, quantity, avg_cost, as_of_date";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Holding> rowMapper = (rs, rowNum) -> new Holding(
            rs.getObject("id", Long.class),
            rs.getString("account"),
            rs.getString("symbol"),
            rs.getObject("quantity", Double.class),
            rs.getObject("avg_cost", Double.class),
            rs.getObject("as_of_date", LocalDate.class)
    );

    public DatabricksHistoricalHoldingsRepository(
            @Qualifier("databricksJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @CircuitBreaker(name = INSTANCE)
    @Retry(name = INSTANCE)
    public List<Holding> findByAccount(String account, LocalDate from, LocalDate to) {
        String sql = "SELECT " + COLUMNS + " FROM historical_holdings "
                + "WHERE account = ? AND as_of_date BETWEEN ? AND ? "
                + "ORDER BY as_of_date, symbol";
        return jdbcTemplate.query(sql, rowMapper, account, from, to);
    }
}
