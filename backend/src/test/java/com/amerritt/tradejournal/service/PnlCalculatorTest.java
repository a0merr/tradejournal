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
                new BigDecimal(qty), OrderStatus.FILLED);
        return new Fill(order, new BigDecimal(qty), new BigDecimal(price), new BigDecimal(fee),
                OffsetDateTime.now());
    }

    @Test
    void longRoundTrip_realizesGain() {
        var r = PnlCalculator.compute(List.of(
                fill(Side.BUY, "2", "100", "0"),
                fill(Side.SELL, "2", "110", "0")));
        assertThat(r.realizedPnl()).isEqualByComparingTo("20");
        assertThat(r.closedTrades()).isEqualTo(1);
        assertThat(r.winRate()).isEqualByComparingTo("1.0");
    }

    @Test
    void partialClose_chargesProratedFees() {
        // BUY 2 @ 100 (fee 0.10 -> 0.05/unit), SELL 1 @ 120 (fee 0.10/unit)
        // matched 1: gross (120-100)=20, fees (0.05 + 0.10) = 0.15 -> 19.85
        var r = PnlCalculator.compute(List.of(
                fill(Side.BUY, "2", "100", "0.10"),
                fill(Side.SELL, "1", "120", "0.10")));
        assertThat(r.realizedPnl()).isEqualByComparingTo("19.85");
        assertThat(r.closedTrades()).isEqualTo(1);
        assertThat(r.winRate()).isEqualByComparingTo("1.0");
    }

    @Test
    void losingTrade_countsAsLoss() {
        var r = PnlCalculator.compute(List.of(
                fill(Side.BUY, "1", "100", "0"),
                fill(Side.SELL, "1", "90", "0")));
        assertThat(r.realizedPnl()).isEqualByComparingTo("-10");
        assertThat(r.closedTrades()).isEqualTo(1);
        assertThat(r.winRate()).isEqualByComparingTo("0.0");
    }

    @Test
    void shortThenCover_realizesGain() {
        // SELL 1 @ 100 opens short; BUY 1 @ 90 covers -> (100 - 90) = 10
        var r = PnlCalculator.compute(List.of(
                fill(Side.SELL, "1", "100", "0"),
                fill(Side.BUY, "1", "90", "0")));
        assertThat(r.realizedPnl()).isEqualByComparingTo("10");
        assertThat(r.closedTrades()).isEqualTo(1);
        assertThat(r.winRate()).isEqualByComparingTo("1.0");
    }

    @Test
    void openPositionOnly_hasNoClosedTrades() {
        var r = PnlCalculator.compute(List.of(fill(Side.BUY, "1", "100", "0")));
        assertThat(r.realizedPnl()).isEqualByComparingTo("0");
        assertThat(r.closedTrades()).isZero();
        assertThat(r.winRate()).isNull();
    }

    @Test
    void emptyStream_isFlat() {
        var r = PnlCalculator.compute(List.of());
        assertThat(r.realizedPnl()).isEqualByComparingTo("0");
        assertThat(r.closedTrades()).isZero();
        assertThat(r.winRate()).isNull();
    }
}
