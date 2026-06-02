package com.example.tradehistory.controller;

import com.example.tradehistory.model.Trade;
import com.example.tradehistory.service.TradeHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeHistoryController {

    private final TradeHistoryService service;

    @GetMapping
    public List<Trade> getTrades(@RequestParam String account) {
        return service.getTradesByAccount(account);
    }
}
