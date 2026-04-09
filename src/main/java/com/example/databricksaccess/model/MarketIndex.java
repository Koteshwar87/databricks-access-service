package com.example.databricksaccess.model;

import java.time.LocalDate;

public record MarketIndex(
        int id,
        String symbol,
        String indexName,
        String country,
        double currentValue,
        double changePct,
        double marketCapTrillions,
        LocalDate tradeDate
) {
}
