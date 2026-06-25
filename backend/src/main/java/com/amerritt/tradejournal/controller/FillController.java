package com.amerritt.tradejournal.controller;

import com.amerritt.tradejournal.dto.FillRequest;
import com.amerritt.tradejournal.dto.FillResponse;
import com.amerritt.tradejournal.dto.ImportResult;
import com.amerritt.tradejournal.security.AuthPrincipal;
import com.amerritt.tradejournal.service.CsvFillImportService;
import com.amerritt.tradejournal.service.FillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
@RequestMapping("/api/fills")
public class FillController {

    private final FillService fillService;
    private final CsvFillImportService csvImportService;

    public FillController(FillService fillService, CsvFillImportService csvImportService) {
        this.fillService = fillService;
        this.csvImportService = csvImportService;
    }

    /** The trading bot's entry point: ingest a single execution. */
    @PostMapping
    public ResponseEntity<FillResponse> ingest(@Valid @RequestBody FillRequest req,
                                               @AuthenticationPrincipal AuthPrincipal principal) {
        FillResponse created = fillService.ingest(req, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Bulk-import fills from a CSV (e.g. a broker statement). Good rows commit; bad rows
     * are reported by line number. Header:
     * {@code symbol,exchange,assetClass,side,type,quantity,price,fee,filledAt}.
     */
    @PostMapping(path = "/import", consumes = "multipart/form-data")
    public ImportResult importCsv(@RequestParam("file") MultipartFile file,
                                  @RequestParam("accountId") Long accountId,
                                  @AuthenticationPrincipal AuthPrincipal principal) {
        try {
            return csvImportService.importCsv(file.getBytes(), accountId, principal.userId());
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read uploaded file", ex);
        }
    }
}
