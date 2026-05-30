package com.example.databricksaccess.repository;

import com.example.databricksaccess.model.MarketIndex;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class MarketIndexRepository {

    private static final String INSTANCE = "databricks";
    private static final String COLUMNS = "id, symbol, index_name, country, current_value, change_pct, market_cap_trillions, trade_date";

    private static final Map<String, String> SORTABLE_COLUMNS = Map.of(
            "id", "id",
            "symbol", "symbol",
            "indexName", "index_name",
            "country", "country",
            "currentValue", "current_value",
            "changePct", "change_pct",
            "marketCapTrillions", "market_cap_trillions",
            "tradeDate", "trade_date"
    );

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
    public Page<MarketIndex> findAll(Pageable pageable) {
        String sql = "SELECT " + COLUMNS + " FROM market_indices "
                + buildOrderBy(pageable.getSort())
                + " LIMIT " + pageable.getPageSize()
                + " OFFSET " + pageable.getOffset();
        List<MarketIndex> rows = jdbcTemplate.query(sql, rowMapper);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM market_indices", Long.class);
        return new PageImpl<>(rows, pageable, total == null ? 0L : total);
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
    public Page<MarketIndex> findByCountry(String country, Pageable pageable) {
        String sql = "SELECT " + COLUMNS + " FROM market_indices WHERE country = ? "
                + buildOrderBy(pageable.getSort())
                + " LIMIT " + pageable.getPageSize()
                + " OFFSET " + pageable.getOffset();
        List<MarketIndex> rows = jdbcTemplate.query(sql, rowMapper, country);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM market_indices WHERE country = ?",
                Long.class, country);
        return new PageImpl<>(rows, pageable, total == null ? 0L : total);
    }

    private String buildOrderBy(Sort sort) {
        if (sort.isUnsorted()) {
            return "ORDER BY id";
        }
        return sort.stream()
                .map(order -> {
                    String column = SORTABLE_COLUMNS.get(order.getProperty());
                    if (column == null) {
                        throw new IllegalArgumentException(
                                "Invalid sort field: " + order.getProperty()
                                        + ". Allowed: " + SORTABLE_COLUMNS.keySet());
                    }
                    return column + " " + order.getDirection();
                })
                .collect(Collectors.joining(", ", "ORDER BY ", ""));
    }
}
