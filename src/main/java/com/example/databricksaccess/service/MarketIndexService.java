package com.example.databricksaccess.service;

import com.example.databricksaccess.exception.MarketIndexNotFoundException;
import com.example.databricksaccess.model.MarketIndex;
import com.example.databricksaccess.repository.MarketIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketIndexService {

    private final MarketIndexRepository repository;

    public List<MarketIndex> getAllIndices() {
        log.debug("Fetching all market indices");
        return repository.findAll();
    }

    public MarketIndex getBySymbol(String symbol) {
        log.debug("Fetching market index for symbol: {}", symbol);
        return repository.findBySymbol(symbol)
                .orElseThrow(() -> new MarketIndexNotFoundException(symbol));
    }

    public List<MarketIndex> getByCountry(String country) {
        log.debug("Fetching market indices for country: {}", country);
        return repository.findByCountry(country);
    }
}
