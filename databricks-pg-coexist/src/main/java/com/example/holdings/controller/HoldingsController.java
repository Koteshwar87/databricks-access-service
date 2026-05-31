package com.example.holdings.controller;

import com.example.holdings.model.Holding;
import com.example.holdings.service.HoldingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/holdings")
@RequiredArgsConstructor
public class HoldingsController {

    private final HoldingsService service;

    @GetMapping("/live")
    public List<Holding> getLive(@RequestParam String account) {
        return service.getLiveHoldings(account);
    }

    @GetMapping("/historical")
    public List<Holding> getHistorical(
            @RequestParam String account,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.getHistoricalHoldings(account, from, to);
    }
}
