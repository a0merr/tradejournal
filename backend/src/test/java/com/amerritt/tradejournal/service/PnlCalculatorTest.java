package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.model.Account;
import com.amerritt.tradejournal.model.Fill;
import com.amerritt.tradejournal.model.Instrument;
import com.amerritt.tradejournal.model.OrderEntity;
import com.amerritt.tradejournal.model.OrderStatus;
import com.amerritt.tradejournal.model.OrderType;
import com.amerritt.tradejournal.model.Side;
import com.amerritt.tradejournal.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests for FIFO lot-matching — no Spring, no database. */
class PnlCalculatorTest {

    private static final Account ACCOUNT = new Account(new User("t@e.com", "h"), "B", "USD");
    private static final Instrument INSTRUMENT = new Instrument("TEST", "EX", "CRYPTO");

    private static Fill fill(Side side, String qty, String price, String fee) {
        OrderEntity order = new OrderEntity(ACCOUNT, INSTRUMENT, side, OrderType.MARKET,
                new BigDecimal(qty), OrderStatus.FILLED, null);
        return new Fill(order, new BigDecimal(qty), new BigDecimal(price), new BigDecimal(fee),
                OffsetDateTime.now());
    }

    private static PnlCalculator.Result compute(Fill... fills) {
        return PnlCalculator.compute(List.of(fills).iterator());
    }

    /** These instruments are unpersisted, so they all share the null id. */
    private static PnlCalculator.OpenPosition open(PnlCalculator.Result result) {
        return result.openByInstrument().get(null);
    }

    @Test
    void longRoundTrip_realizesGain() {
        var r = compute(
                fill(Side.BUY, "2", "100", "0"),
                fill(Side.SELL, "2", "110", "0"));
        assertThat(r.realizedPnl()).isEqualByComparingTo("20");
        assertThat(r.closedTrades()).isEqualTo(1);
        assertThat(r.winRate()).isEqualByComparingTo("1.0");
    }

    @Test
    void partialClose_chargesProratedFees() {
        // BUY 2 @ 100 (fee 0.10 -> 0.05/unit), SELL 1 @ 120 (fee 0.10/unit)
        // matched 1: gross (120-100)=20, fees (0.05 + 0.10) = 0.15 -> 19.85
        var r = compute(
                fill(Side.BUY, "2", "100", "0.10"),
                fill(Side.SELL, "1", "120", "0.10"));
        assertThat(r.realizedPnl()).isEqualByComparingTo("19.85");
        assertThat(r.closedTrades()).isEqualTo(1);
        assertThat(r.winRate()).isEqualByComparingTo("1.0");
    }

    @Test
    void losingTrade_countsAsLoss() {
        var r = compute(
                fill(Side.BUY, "1", "100", "0"),
                fill(Side.SELL, "1", "90", "0"));
        assertThat(r.realizedPnl()).isEqualByComparingTo("-10");
        assertThat(r.closedTrades()).isEqualTo(1);
        assertThat(r.winRate()).isEqualByComparingTo("0.0");
    }

    @Test
    void shortThenCover_realizesGain() {
        // SELL 1 @ 100 opens short; BUY 1 @ 90 covers -> (100 - 90) = 10
        var r = compute(
                fill(Side.SELL, "1", "100", "0"),
                fill(Side.BUY, "1", "90", "0"));
        assertThat(r.realizedPnl()).isEqualByComparingTo("10");
        assertThat(r.closedTrades()).isEqualTo(1);
        assertThat(r.winRate()).isEqualByComparingTo("1.0");
    }

    @Test
    void openPositionOnly_hasNoClosedTrades() {
        var r = compute(fill(Side.BUY, "1", "100", "0"));
        assertThat(r.realizedPnl()).isEqualByComparingTo("0");
        assertThat(r.closedTrades()).isZero();
        assertThat(r.winRate()).isNull();
    }

    @Test
    void emptyStream_isFlat() {
        var r = PnlCalculator.compute(List.<Fill>of().iterator());
        assertThat(r.realizedPnl()).isEqualByComparingTo("0");
        assertThat(r.closedTrades()).isZero();
        assertThat(r.openByInstrument()).isEmpty();
        assertThat(r.winRate()).isNull();
    }

    // --- cost basis of what is still open ---

    @Test
    void openBasis_ignoresThePriceOfWhatWasSold() {
        // BUY 2 @ 100, SELL 1 @ 120. The remaining unit was bought at 100 — the sale price is
        // irrelevant to its basis. Averaging both fills would have said 106.66666667.
        var r = compute(
                fill(Side.BUY, "2", "100", "0"),
                fill(Side.SELL, "1", "120", "0"));
        assertThat(open(r).netQuantity()).isEqualByComparingTo("1");
        assertThat(open(r).avgEntryPrice()).isEqualByComparingTo("100");
    }

    @Test
    void openBasis_isQuantityWeightedAcrossRemainingLots() {
        // BUY 1 @ 100 and BUY 3 @ 200 -> (1*100 + 3*200) / 4 = 175
        var r = compute(
                fill(Side.BUY, "1", "100", "0"),
                fill(Side.BUY, "3", "200", "0"));
        assertThat(open(r).netQuantity()).isEqualByComparingTo("4");
        assertThat(open(r).avgEntryPrice()).isEqualByComparingTo("175");
    }

    @Test
    void openBasis_dropsTheOldestLotFirst() {
        // FIFO: the SELL consumes the 100 lot, leaving the 200 lot as the basis.
        var r = compute(
                fill(Side.BUY, "1", "100", "0"),
                fill(Side.BUY, "1", "200", "0"),
                fill(Side.SELL, "1", "150", "0"));
        assertThat(open(r).avgEntryPrice()).isEqualByComparingTo("200");
    }

    @Test
    void flatPosition_reportsNoOpenBasis() {
        var r = compute(
                fill(Side.BUY, "1", "100", "0"),
                fill(Side.SELL, "1", "110", "0"));
        assertThat(r.openByInstrument()).isEmpty();
    }

    @Test
    void shortPosition_reportsNegativeQuantityAtEntryPrice() {
        var r = compute(fill(Side.SELL, "2", "100", "0"));
        assertThat(open(r).netQuantity()).isEqualByComparingTo("-2");
        assertThat(open(r).avgEntryPrice()).isEqualByComparingTo("100");
    }

    // --- one closing fill is one trade ---

    @Test
    void oneSellClosingThreeLots_isOneTrade() {
        // Three separate entries, one exit. Counting per lot would report three trades and let
        // the win rate move purely on how fragmented the entries happened to be.
        var r = compute(
                fill(Side.BUY, "1", "100", "0"),
                fill(Side.BUY, "1", "100", "0"),
                fill(Side.BUY, "1", "100", "0"),
                fill(Side.SELL, "3", "110", "0"));
        assertThat(r.closedTrades()).isEqualTo(1);
        assertThat(r.realizedPnl()).isEqualByComparingTo("30");
        assertThat(r.winRate()).isEqualByComparingTo("1.0");
    }

    @Test
    void tradeIsWonOrLostOnItsCombinedResult() {
        // Exit at 105 beats the 100 lot and loses to the 120 lot. Net +(5) + (-15) = -10:
        // one losing trade, not one win and one loss.
        var r = compute(
                fill(Side.BUY, "1", "100", "0"),
                fill(Side.BUY, "1", "120", "0"),
                fill(Side.SELL, "2", "105", "0"));
        assertThat(r.closedTrades()).isEqualTo(1);
        assertThat(r.realizedPnl()).isEqualByComparingTo("-10");
        assertThat(r.winRate()).isEqualByComparingTo("0.0");
    }

    @Test
    void flippingFillCountsTheCloseAndOpensTheRemainder() {
        // Long 1 @ 100, then SELL 3 @ 110: closes the long (one trade) and opens a short 2.
        var r = compute(
                fill(Side.BUY, "1", "100", "0"),
                fill(Side.SELL, "3", "110", "0"));
        assertThat(r.closedTrades()).isEqualTo(1);
        assertThat(r.realizedPnl()).isEqualByComparingTo("10");
        assertThat(open(r).netQuantity()).isEqualByComparingTo("-2");
        assertThat(open(r).avgEntryPrice()).isEqualByComparingTo("110");
    }
}
