package com.example.tradehistory.model;

import java.time.LocalDateTime;

public record Trade(
        Long id,
        String account,
        String symbol,
        String side,
        Double quantity,
        Double price,
        LocalDateTime execTime
) {
}
