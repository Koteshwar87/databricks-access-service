package com.example.databricksaccess.repository;

import com.example.databricksaccess.model.MarketIndex;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class MarketIndexRepository {

    private static final String INSTANCE = "databricks";
    private static final String COLUMNS = "id, symbol, index_name, country, current_value, change_pct, market_cap_trillions, trade_date";
    private static final int FIND_ALL_HARD_LIMIT = 10000;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<MarketIndex> rowMapper = (rs, rowNum) -> new MarketIndex(
            rs.getObject("id", Integer.class),
            rs.getString("symbol"),
            rs.getString("index_name"),
            rs.getString("country"),
            rs.getObject("current_value", Double.class),
            rs.getObject("change_pct", Double.class),
            rs.getObject("market_cap_trillions", Double.class),
            rs.getObject("trade_date", LocalDate.class)
    );

    public MarketIndexRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @CircuitBreaker(name = INSTANCE)
    @Retry(name = INSTANCE)
    public List<MarketIndex> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM market_indices LIMIT " + FIND_ALL_HARD_LIMIT;
        return jdbcTemplate.query(sql, rowMapper);
    }

    @CircuitBreaker(name = INSTANCE)
    @Retry(name = INSTANCE)
    public Optional<MarketIndex> findBySymbol(String symbol) {
        String sql = "SELECT " + COLUMNS + " FROM market_indices WHERE symbol = ?";
        List<MarketIndex> results = jdbcTemplate.query(sql, rowMapper, symbol);
        return results.stream().findFirst();
    }

    @CircuitBreaker(name = INSTANCE)
    @Retry(name = INSTANCE)
    public List<MarketIndex> findByCountry(String country) {
        String sql = "SELECT " + COLUMNS + " FROM market_indices WHERE country = ?";
        return jdbcTemplate.query(sql, rowMapper, country);
    }
}
