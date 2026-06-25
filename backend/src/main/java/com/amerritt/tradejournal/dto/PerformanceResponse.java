package com.amerritt.tradejournal.dto;

import java.math.BigDecimal;

/**
 * Account-level summary reconstructed from fills.
 *
 * <p>{@code openExposure} is the absolute notional currently held
 * (sum of |net_quantity| * avg_price across open positions). {@code realizedPnl},
 * {@code winRate}, and {@code closedTrades} come from FIFO lot-matching of closing
 * fills against opening fills (fees included). {@code winRate} is a fraction in
 * [0, 1], or {@code null} when no trade has been closed yet.
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
