package com.example.tradehistory.service;

import com.example.tradehistory.model.Trade;
import com.example.tradehistory.repository.DatabricksTradeRepository;
import com.example.tradehistory.repository.PgTradeRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeHistoryService {

    private final DatabricksTradeRepository databricksRepo;
    private final PgTradeRepository pgRepo;

    @Retry(name = "databricks")
    @CircuitBreaker(name = "databricks", fallbackMethod = "getFromPg")
    public List<Trade> getTradesByAccount(String account) {
        log.debug("Fetching trades from Databricks for account: {}", account);
        return databricksRepo.findByAccount(account);
    }

    @SuppressWarnings("unused")
    private List<Trade> getFromPg(String account, Throwable cause) {
        log.warn("Databricks failed for account={}, falling back to PG: {}",
                account, cause.getMessage());
        return pgRepo.findByAccount(account);
    }
}
