package com.amerritt.tradejournal.dto;

import java.math.BigDecimal;

/**
 * Account-level summary reconstructed from fills.
 *
 * <p>Everything here comes from one FIFO replay of the account's fills, so the figures agree
 * with each other. {@code openExposure} is the absolute notional still held, priced at the
 * FIFO cost basis of the unmatched lots. {@code realizedPnl}, {@code winRate}, and
 * {@code closedTrades} come from matching closing fills against opening lots, fees included;
 * one closing fill is one trade however many lots it consumed. {@code winRate} is a fraction
 * in [0, 1], or {@code null} when no trade has been closed yet.
 */
public record PerformanceResponse(
        Long accountId,
        long totalOrders,
        long totalFills,
        long openPositions,
        long closedTrades,
        BigDecimal totalFees,
        BigDecimal grossNotional,
        BigDecimal openExposure,
        BigDecimal realizedPnl,
        BigDecimal winRate) {
}
