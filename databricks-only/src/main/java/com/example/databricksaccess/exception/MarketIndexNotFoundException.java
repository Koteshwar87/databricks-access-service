package com.example.databricksaccess.exception;

public class MarketIndexNotFoundException extends RuntimeException {

    public MarketIndexNotFoundException(String symbol) {
        super("Market index not found with symbol: " + symbol);
    }
}
