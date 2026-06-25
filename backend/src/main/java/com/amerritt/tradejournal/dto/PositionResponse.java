package com.amerritt.tradejournal.dto;

import com.amerritt.tradejournal.model.Position;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PositionResponse(
        Long accountId,
        Long instrumentId,
        String symbol,
        String exchange,
        BigDecimal netQuantity,
        BigDecimal avgPrice,
        BigDecimal totalFees,
        long fillCount,
        OffsetDateTime lastFilledAt) {

    public static PositionResponse from(Position p) {
        return new PositionResponse(
                p.getAccountId(),
                p.getInstrumentId(),
                p.getSymbol(),
                p.getExchange(),
                p.getNetQuantity(),
                p.getAvgPrice(),
                p.getTotalFees(),
                p.getFillCount(),
                p.getLastFilledAt());
    }
}
