package com.example.databricksaccess.controller;

import com.example.databricksaccess.model.MarketIndex;
import com.example.databricksaccess.service.MarketIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/indices")
@RequiredArgsConstructor
public class MarketIndexController {

    private final MarketIndexService service;

    @GetMapping
    public List<MarketIndex> getAll(@RequestParam(required = false) String country) {
        if (country != null && !country.isBlank()) {
            return service.getByCountry(country);
        }
        return service.getAllIndices();
    }

    @GetMapping("/{symbol}")
    public MarketIndex getBySymbol(@PathVariable String symbol) {
        return service.getBySymbol(symbol.toUpperCase());
    }
}
