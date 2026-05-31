package com.example.databricksaccess.service;

import com.example.databricksaccess.exception.MarketIndexNotFoundException;
import com.example.databricksaccess.model.MarketIndex;
import com.example.databricksaccess.repository.MarketIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketIndexService {

    private final MarketIndexRepository repository;

    public Page<MarketIndex> getAllIndices(Pageable pageable) {
        log.debug("Fetching market indices page {}", pageable);
        return repository.findAll(pageable);
    }

    public MarketIndex getBySymbol(String symbol) {
        log.debug("Fetching market index for symbol: {}", symbol);
        return repository.findBySymbol(symbol)
                .orElseThrow(() -> new MarketIndexNotFoundException(symbol));
    }

    public Page<MarketIndex> getByCountry(String country, Pageable pageable) {
        log.debug("Fetching market indices for country: {} page {}", country, pageable);
        return repository.findByCountry(country, pageable);
    }
}
