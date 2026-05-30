package com.example.databricksaccess.model;

import java.time.LocalDate;

public record MarketIndex(
        int id,
        String symbol,
        String indexName,
        String country,
        Double currentValue,
        Double changePct,
        Double marketCapTrillions,
        LocalDate tradeDate
) {
}
