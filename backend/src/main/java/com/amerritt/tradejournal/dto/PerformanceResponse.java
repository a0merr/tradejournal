package com.amerritt.tradejournal.dto;

import java.math.BigDecimal;

/**
 * Account-level summary reconstructed from fills.
 *
 * <p>{@code openExposure} is the absolute notional currently held
 * (sum of |net_quantity| * avg_price across open positions). Realized PnL and
 * win rate require lot-matching of closing fills against opening fills, which is
 * on the roadmap; until then those fields are reported as {@code null} rather
 * than a misleading zero.
 */
public record PerformanceResponse(
        Long accountId,
        long totalOrders,
        long totalFills,
        long openPositions,
        BigDecimal totalFees,
        BigDecimal grossNotional,
        BigDecimal openExposure,
        BigDecimal realizedPnl,
        BigDecimal winRate) {
}
