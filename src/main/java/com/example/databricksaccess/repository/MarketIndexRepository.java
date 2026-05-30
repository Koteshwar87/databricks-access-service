package com.example.databricksaccess.repository;

import com.example.databricksaccess.config.DatabricksProperties;
import com.example.databricksaccess.model.MarketIndex;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MarketIndexRepository {

    private static final String INSTANCE = "databricks";

    private final JdbcTemplate jdbcTemplate;
    private final String schema;

    private final RowMapper<MarketIndex> rowMapper = (rs, rowNum) -> new MarketIndex(
            rs.getInt("id"),
            rs.getString("symbol"),
            rs.getString("index_name"),
            rs.getString("country"),
            rs.getDouble("current_value"),
            rs.getDouble("change_pct"),
            rs.getDouble("market_cap_trillions"),
            rs.getDate("trade_date").toLocalDate()
    );

    public MarketIndexRepository(JdbcTemplate jdbcTemplate, DatabricksProperties databricksProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.schema = databricksProperties.getSchema();
    }

    @CircuitBreaker(name = INSTANCE)
    @Retry(name = INSTANCE)
    public List<MarketIndex> findAll() {
        String sql = "SELECT id, symbol, index_name, country, current_value, change_pct, market_cap_trillions, trade_date FROM " + schema + ".market_indices";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @CircuitBreaker(name = INSTANCE)
    @Retry(name = INSTANCE)
    public Optional<MarketIndex> findBySymbol(String symbol) {
        String sql = "SELECT id, symbol, index_name, country, current_value, change_pct, market_cap_trillions, trade_date FROM " + schema + ".market_indices WHERE symbol = ?";
        List<MarketIndex> results = jdbcTemplate.query(sql, rowMapper, symbol);
        return results.stream().findFirst();
    }

    @CircuitBreaker(name = INSTANCE)
    @Retry(name = INSTANCE)
    public List<MarketIndex> findByCountry(String country) {
        String sql = "SELECT id, symbol, index_name, country, current_value, change_pct, market_cap_trillions, trade_date FROM " + schema + ".market_indices WHERE country = ?";
        return jdbcTemplate.query(sql, rowMapper, country);
    }
}
