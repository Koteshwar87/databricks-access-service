package com.example.databricksaccess.controller;

import com.example.databricksaccess.model.MarketIndex;
import com.example.databricksaccess.service.MarketIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/indices")
@RequiredArgsConstructor
public class MarketIndexController {

    private final MarketIndexService service;

    @GetMapping
    public Page<MarketIndex> getAll(
            @RequestParam(required = false) String country,
            Pageable pageable) {
        if (country != null && !country.isBlank()) {
            return service.getByCountry(country, pageable);
        }
        return service.getAllIndices(pageable);
    }

    @GetMapping("/{symbol}")
    public MarketIndex getBySymbol(@PathVariable String symbol) {
        return service.getBySymbol(symbol.toUpperCase());
    }
}
