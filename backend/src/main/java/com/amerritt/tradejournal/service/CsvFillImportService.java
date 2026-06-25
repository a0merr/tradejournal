package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.dto.FillRequest;
import com.amerritt.tradejournal.dto.ImportResult;
import com.amerritt.tradejournal.model.OrderType;
import com.amerritt.tradejournal.model.Side;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports fills from a CSV (e.g. a broker statement export). Expected header:
 * {@code symbol,exchange,assetClass,side,type,quantity,price,fee,filledAt}
 * ({@code fee} and {@code filledAt} optional).
 *
 * <p>Each row is ingested in its own transaction via {@link FillService}, so a bad row
 * is reported and skipped without rolling back the rows that already succeeded.
 */
@Service
public class CsvFillImportService {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .get();

    private final FillService fillService;

    public CsvFillImportService(FillService fillService) {
        this.fillService = fillService;
    }

    public ImportResult importCsv(byte[] content, Long accountId, Long userId) {
        List<ImportResult.RowError> errors = new ArrayList<>();
        int imported = 0;

        try (Reader reader = new BufferedReader(
                new InputStreamReader(new java.io.ByteArrayInputStream(content), StandardCharsets.UTF_8));
             CSVParser parser = CSVParser.parse(reader, FORMAT)) {

            for (CSVRecord record : parser) {
                long line = record.getRecordNumber() + 1; // +1: header is line 1
                try {
                    fillService.ingest(toRequest(record, accountId), userId);
                    imported++;
                } catch (Exception ex) {
                    errors.add(new ImportResult.RowError(line, rootMessage(ex)));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read CSV", ex);
        }

        return new ImportResult(imported, errors.size(), errors);
    }

    private FillRequest toRequest(CSVRecord r, Long accountId) {
        BigDecimal fee = parseOptionalDecimal(get(r, "fee"));
        OffsetDateTime filledAt = parseOptionalTime(get(r, "filledAt"));
        return new FillRequest(
                accountId,
                require(r, "symbol"),
                require(r, "exchange"),
                require(r, "assetClass"),
                Side.valueOf(require(r, "side").toUpperCase()),
                OrderType.valueOf(require(r, "type").toUpperCase()),
                new BigDecimal(require(r, "quantity")),
                new BigDecimal(require(r, "price")),
                fee,
                filledAt);
    }

    private static String get(CSVRecord r, String col) {
        return r.isMapped(col) ? r.get(col) : null;
    }

    private static String require(CSVRecord r, String col) {
        String v = get(r, col);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required column '" + col + "'");
        }
        return v;
    }

    private static BigDecimal parseOptionalDecimal(String v) {
        return (v == null || v.isBlank()) ? null : new BigDecimal(v);
    }

    private static OffsetDateTime parseOptionalTime(String v) {
        return (v == null || v.isBlank()) ? null : OffsetDateTime.parse(v);
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return (msg == null || msg.isBlank()) ? cur.getClass().getSimpleName() : msg;
    }
}
