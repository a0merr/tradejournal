package com.amerritt.tradejournal.dto;

import com.amerritt.tradejournal.model.Position;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A position reconstructed from fills.
 *
 * <p>Two prices, because they answer different questions and conflating them produced a
 * meaningless number for any instrument that had been partially sold:
 *
 * <ul>
 *   <li>{@code avgEntryPrice} — FIFO cost basis of what is still open. The one to compare a
 *       market price against. Null once the position is flat.
 *   <li>{@code avgFillPrice} — quantity-weighted average across every fill, buys and sells
 *       alike. An execution-quality figure, never a basis.
 * </ul>
 */
public record PositionResponse(
        Long accountId,
        Long instrumentId,
        String symbol,
        String exchange,
        BigDecimal netQuantity,
        BigDecimal avgEntryPrice,
        BigDecimal avgFillPrice,
        BigDecimal totalFees,
        long fillCount,
        OffsetDateTime lastFilledAt) {

    public static PositionResponse from(Position p, BigDecimal avgEntryPrice) {
        return new PositionResponse(
                p.getAccountId(),
                p.getInstrumentId(),
                p.getSymbol(),
                p.getExchange(),
                p.getNetQuantity(),
                avgEntryPrice,
                p.getAvgFillPrice(),
                p.getTotalFees(),
                p.getFillCount(),
                p.getLastFilledAt());
    }
}
