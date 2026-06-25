package com.amerritt.tradejournal.controller;

import com.amerritt.tradejournal.dto.FillRequest;
import com.amerritt.tradejournal.dto.FillResponse;
import com.amerritt.tradejournal.security.AuthPrincipal;
import com.amerritt.tradejournal.service.FillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fills")
public class FillController {

    private final FillService fillService;

    public FillController(FillService fillService) {
        this.fillService = fillService;
    }

    /** The trading bot's entry point: ingest a single execution. */
    @PostMapping
    public ResponseEntity<FillResponse> ingest(@Valid @RequestBody FillRequest req,
                                               @AuthenticationPrincipal AuthPrincipal principal) {
        FillResponse created = fillService.ingest(req, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
