package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.model.Fill;
import com.amerritt.tradejournal.model.Side;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconstructs realized PnL and win rate from a fill stream using FIFO lot-matching,
 * per instrument. A BUY opens (or extends) a long lot; a SELL closes the oldest open
 * long — or, with no long inventory, opens a short, which a later BUY closes. Positions
 * may flip within a single fill (e.g. selling more than is held). Fees on both the
 * opening and closing legs are charged against the realized result, prorated by the
 * matched quantity.
 *
 * <p>Pure and deterministic: no Spring, no database — unit-testable in isolation.
 */
public final class PnlCalculator {

    private static final int SCALE = 8;

    /** One open lot: a signed remaining quantity at an entry price, with its per-unit fee. */
    private static final class Lot {
        BigDecimal qty;        // signed: positive = long, negative = short
        final BigDecimal price;
        final BigDecimal feePerUnit;

        Lot(BigDecimal qty, BigDecimal price, BigDecimal feePerUnit) {
            this.qty = qty;
            this.price = price;
            this.feePerUnit = feePerUnit;
        }
    }

    public record Result(BigDecimal realizedPnl, BigDecimal winRate, long closedTrades) {
    }

    private PnlCalculator() {
    }

    /**
     * @param fills fills ordered by execution time (ascending); each carries its order's side
     */
    public static Result compute(List<Fill> fills) {
        // FIFO queue of open lots per instrument
        Map<Long, Deque<Lot>> openByInstrument = new LinkedHashMap<>();

        BigDecimal realized = BigDecimal.ZERO;
        long closedTrades = 0;
        long winningTrades = 0;

        for (Fill f : fills) {
            Long instrumentId = f.getOrder().getInstrument().getId();
            boolean buy = f.getOrder().getSide() == Side.BUY;
            BigDecimal signed = buy ? f.getQuantity() : f.getQuantity().negate();
            BigDecimal feePerUnit = perUnitFee(f);

            Deque<Lot> open = openByInstrument.computeIfAbsent(instrumentId, k -> new ArrayDeque<>());

            BigDecimal remaining = signed;
            // Close against opposite-signed lots at the front of the queue.
            while (remaining.signum() != 0 && !open.isEmpty()
                    && open.peekFirst().qty.signum() == -remaining.signum()) {
                Lot lot = open.peekFirst();
                BigDecimal matchQty = remaining.abs().min(lot.qty.abs());

                // Long lot closed by a sell -> (exit - entry); short lot closed by a buy -> (entry - exit).
                BigDecimal gross = lot.qty.signum() > 0
                        ? f.getPrice().subtract(lot.price).multiply(matchQty)
                        : lot.price.subtract(f.getPrice()).multiply(matchQty);
                BigDecimal fees = lot.feePerUnit.add(feePerUnit).multiply(matchQty);
                BigDecimal net = gross.subtract(fees);

                realized = realized.add(net);
                closedTrades++;
                if (net.signum() > 0) {
                    winningTrades++;
                }

                // Reduce both the lot and the remaining fill toward the match.
                lot.qty = lot.qty.subtract(BigDecimal.valueOf(lot.qty.signum()).multiply(matchQty));
                remaining = remaining.subtract(BigDecimal.valueOf(remaining.signum()).multiply(matchQty));
                if (lot.qty.signum() == 0) {
                    open.pollFirst();
                }
            }

            // Whatever is left opens a new lot in the fill's direction.
            if (remaining.signum() != 0) {
                open.addLast(new Lot(remaining, f.getPrice(), feePerUnit));
            }
        }

        BigDecimal winRate = closedTrades == 0
                ? null
                : BigDecimal.valueOf(winningTrades)
                        .divide(BigDecimal.valueOf(closedTrades), 4, RoundingMode.HALF_UP);

        return new Result(realized.setScale(SCALE, RoundingMode.HALF_UP), winRate, closedTrades);
    }

    private static BigDecimal perUnitFee(Fill f) {
        if (f.getFee() == null || f.getFee().signum() == 0 || f.getQuantity().signum() == 0) {
            return BigDecimal.ZERO;
        }
        return f.getFee().divide(f.getQuantity(), SCALE, RoundingMode.HALF_UP);
    }
}
