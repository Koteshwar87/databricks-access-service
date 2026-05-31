package com.example.holdings.service;

import com.example.holdings.config.DataLocationRegistry;
import com.example.holdings.model.Holding;
import com.example.holdings.repository.DatabricksHistoricalHoldingsRepository;
import com.example.holdings.repository.PgLiveHoldingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingsService {

    private final DataLocationRegistry registry;
    private final DatabricksHistoricalHoldingsRepository databricksRepo;
    private final PgLiveHoldingsRepository pgRepo;

    public List<Holding> getLiveHoldings(String account) {
        log.debug("Live holdings request for account: {}", account);
        return switch (registry.locationOf("live")) {
            case POSTGRES -> pgRepo.findByAccount(account);
            case DATABRICKS -> throw new IllegalStateException(
                    "Live holdings not configured for Databricks in this demo "
                            + "(no Databricks repo implements live access; flip routing back to postgres or seed a Databricks live table)");
        };
    }

    public List<Holding> getHistoricalHoldings(String account, LocalDate from, LocalDate to) {
        log.debug("Historical holdings request for account: {} from {} to {}", account, from, to);
        return switch (registry.locationOf("historical")) {
            case DATABRICKS -> databricksRepo.findByAccount(account, from, to);
            case POSTGRES -> throw new IllegalStateException(
                    "Historical holdings not configured for Postgres in this demo "
                            + "(no PG repo implements historical access; flip routing back to databricks or seed a PG historical table)");
        };
    }
}
