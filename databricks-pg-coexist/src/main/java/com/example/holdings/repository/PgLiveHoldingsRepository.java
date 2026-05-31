package com.example.holdings.repository;

import com.example.holdings.model.Holding;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class PgLiveHoldingsRepository {

    private static final String COLUMNS = "id, account, symbol, quantity, avg_cost, last_updated";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Holding> rowMapper = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("last_updated");
        return new Holding(
                rs.getObject("id", Long.class),
                rs.getString("account"),
                rs.getString("symbol"),
                rs.getObject("quantity", Double.class),
                rs.getObject("avg_cost", Double.class),
                ts == null ? null : ts.toLocalDateTime().toLocalDate()
        );
    };

    public PgLiveHoldingsRepository(@Qualifier("pgJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Holding> findByAccount(String account) {
        String sql = "SELECT " + COLUMNS + " FROM live_holdings WHERE account = ? ORDER BY symbol";
        return jdbcTemplate.query(sql, rowMapper, account);
    }
}
