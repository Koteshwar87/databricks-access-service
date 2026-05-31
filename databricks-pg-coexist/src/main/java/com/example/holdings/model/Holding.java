package com.example.holdings.model;

import java.time.LocalDate;

public record Holding(
        Long id,
        String account,
        String symbol,
        Double quantity,
        Double avgCost,
        LocalDate asOfDate
) {
}
