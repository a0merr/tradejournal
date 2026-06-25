package com.amerritt.tradejournal.controller;

import com.amerritt.tradejournal.dto.PerformanceResponse;
import com.amerritt.tradejournal.security.AuthPrincipal;
import com.amerritt.tradejournal.service.PerformanceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private final PerformanceService performanceService;

    public PerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @GetMapping
    public PerformanceResponse summary(@RequestParam Long accountId,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        return performanceService.summarize(accountId, principal.userId());
    }
}
