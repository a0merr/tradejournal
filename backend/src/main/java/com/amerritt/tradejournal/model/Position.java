package com.amerritt.tradejournal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Read-only mapping over the {@code positions} SQL view. Derived from fills,
 * so it is {@link Immutable}; {@link Synchronize} tells Hibernate which tables
 * feed the view, so flushes happen before the view is queried.
 */
@Entity
@Immutable
@Subselect("SELECT account_id, instrument_id, symbol, exchange, net_quantity, avg_fill_price, "
        + "total_fees, fill_count, last_filled_at FROM positions")
@Synchronize({"fills", "orders", "instruments"})
@IdClass(PositionId.class)
public class Position {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Id
    @Column(name = "instrument_id")
    private Long instrumentId;

    private String symbol;

    private String exchange;

    @Column(name = "net_quantity")
    private BigDecimal netQuantity;

    /**
     * Quantity-weighted average price across <em>every</em> fill, buys and sells alike. An
     * execution-quality figure, not a cost basis — the basis of what is still open is FIFO and
     * comes from {@link com.amerritt.tradejournal.service.PnlCalculator}.
     */
    @Column(name = "avg_fill_price")
    private BigDecimal avgFillPrice;

    @Column(name = "total_fees")
    private BigDecimal totalFees;

    @Column(name = "fill_count")
    private long fillCount;

    @Column(name = "last_filled_at")
    private OffsetDateTime lastFilledAt;

    protected Position() {
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getExchange() {
        return exchange;
    }

    public BigDecimal getNetQuantity() {
        return netQuantity;
    }

    public BigDecimal getAvgFillPrice() {
        return avgFillPrice;
    }

    public BigDecimal getTotalFees() {
        return totalFees;
    }

    public long getFillCount() {
        return fillCount;
    }

    public OffsetDateTime getLastFilledAt() {
        return lastFilledAt;
    }
}
