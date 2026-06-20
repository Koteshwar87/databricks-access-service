package com.example.tradehistory.repository;

import com.example.tradehistory.model.Trade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class PgTradeRepository {

    private static final String COLUMNS = "id, account, symbol, side, quantity, price, exec_time";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Trade> rowMapper = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("exec_time");
        return new Trade(
                rs.getObject("id", Long.class),
                rs.getString("account"),
                rs.getString("symbol"),
                rs.getString("side"),
                rs.getObject("quantity", Double.class),
                rs.getObject("price", Double.class),
                ts == null ? null : ts.toLocalDateTime()
        );
    };

    public PgTradeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Trade> findByAccount(String account) {
        String sql = "SELECT " + COLUMNS + " FROM trades WHERE account = ? ORDER BY exec_time DESC";
        return jdbcTemplate.query(sql, rowMapper, account);
    }
}
