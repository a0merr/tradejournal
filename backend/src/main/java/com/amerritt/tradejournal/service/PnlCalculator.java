package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.model.Fill;
import com.amerritt.tradejournal.model.Side;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reconstructs realized PnL, win rate, and open-position cost basis from a fill stream using
 * FIFO lot-matching, per instrument. A BUY opens (or extends) a long lot; a SELL closes the
 * oldest open long — or, with no long inventory, opens a short, which a later BUY closes.
 * Positions may flip within a single fill (e.g. selling more than is held). Fees on both the
 * opening and closing legs are charged against the realized result, prorated by the matched
 * quantity.
 *
 * <p><b>A closed trade is one closing fill, not one matched lot.</b> A single SELL that closes
 * three separate BUY lots is one trade, won or lost on its combined result. Counting per lot
 * would let a trader's win rate move purely by how fragmented their entries happened to be.
 *
 * <p>Consumes an {@link Iterator} so the caller can stream straight from the database: peak
 * memory is proportional to the number of <em>open lots</em>, not to the length of the fill
 * history.
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

    /**
     * What remains open on one instrument once every closing fill has been matched.
     *
     * @param netQuantity  signed: positive long, negative short
     * @param avgEntryPrice quantity-weighted price of the unmatched lots — the FIFO cost basis,
     *                      consistent with how {@code realizedPnl} was computed
     */
    public record OpenPosition(BigDecimal netQuantity, BigDecimal avgEntryPrice) {
    }

    public record Result(BigDecimal realizedPnl, BigDecimal winRate, long closedTrades,
                         Map<Long, OpenPosition> openByInstrument) {
    }

    private PnlCalculator() {
    }

    /**
     * @param fills fills in execution order (ascending); each carries its order's side.
     *              Consumed once.
     */
    public static Result compute(Iterator<Fill> fills) {
        // FIFO queue of open lots per instrument
        Map<Long, Deque<Lot>> openByInstrument = new LinkedHashMap<>();

        BigDecimal realized = BigDecimal.ZERO;
        long closedTrades = 0;
        long winningTrades = 0;

        while (fills.hasNext()) {
            Fill f = fills.next();
            Long instrumentId = f.getOrder().getInstrument().getId();
            boolean buy = f.getOrder().getSide() == Side.BUY;
            BigDecimal signed = buy ? f.getQuantity() : f.getQuantity().negate();
            BigDecimal feePerUnit = perUnitFee(f);

            Deque<Lot> open = openByInstrument.computeIfAbsent(instrumentId, k -> new ArrayDeque<>());

            BigDecimal remaining = signed;
            // Everything this one fill closes is a single trade, so its result is accumulated
            // across however many lots it happens to consume.
            BigDecimal tradeResult = BigDecimal.ZERO;
            boolean closedAnything = false;

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

                tradeResult = tradeResult.add(gross.subtract(fees));
                closedAnything = true;

                // Reduce both the lot and the remaining fill toward the match.
                lot.qty = lot.qty.subtract(BigDecimal.valueOf(lot.qty.signum()).multiply(matchQty));
                remaining = remaining.subtract(BigDecimal.valueOf(remaining.signum()).multiply(matchQty));
                if (lot.qty.signum() == 0) {
                    open.pollFirst();
                }
            }

            if (closedAnything) {
                realized = realized.add(tradeResult);
                closedTrades++;
                if (tradeResult.signum() > 0) {
                    winningTrades++;
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

        return new Result(realized.setScale(SCALE, RoundingMode.HALF_UP), winRate, closedTrades,
                summarize(openByInstrument));
    }

    /** Collapses the remaining lots per instrument into a net quantity and a cost basis. */
    private static Map<Long, OpenPosition> summarize(Map<Long, Deque<Lot>> openByInstrument) {
        Map<Long, OpenPosition> summary = new HashMap<>();
        openByInstrument.forEach((instrumentId, lots) -> {
            BigDecimal net = BigDecimal.ZERO;
            BigDecimal notional = BigDecimal.ZERO;
            BigDecimal absQty = BigDecimal.ZERO;
            for (Lot lot : lots) {
                net = net.add(lot.qty);
                notional = notional.add(lot.qty.abs().multiply(lot.price));
                absQty = absQty.add(lot.qty.abs());
            }
            if (absQty.signum() == 0) {
                return; // flat: nothing open, so there is no basis to report
            }
            summary.put(instrumentId, new OpenPosition(
                    net, notional.divide(absQty, SCALE, RoundingMode.HALF_UP)));
        });
        return summary;
    }

    private static BigDecimal perUnitFee(Fill f) {
        if (f.getFee() == null || f.getFee().signum() == 0 || f.getQuantity().signum() == 0) {
            return BigDecimal.ZERO;
        }
        return f.getFee().divide(f.getQuantity(), SCALE, RoundingMode.HALF_UP);
    }
}
