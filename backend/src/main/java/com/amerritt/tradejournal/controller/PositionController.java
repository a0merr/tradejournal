package com.amerritt.tradejournal.controller;

import com.amerritt.tradejournal.dto.PositionResponse;
import com.amerritt.tradejournal.security.AuthPrincipal;
import com.amerritt.tradejournal.service.PositionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/positions")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping
    public List<PositionResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return positionService.listForUser(principal.userId());
    }
}
