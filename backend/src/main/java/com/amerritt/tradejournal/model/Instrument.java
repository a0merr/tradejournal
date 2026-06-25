package com.amerritt.tradejournal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "instruments",
        uniqueConstraints = @UniqueConstraint(name = "uq_instruments_symbol_exchange",
                columnNames = {"symbol", "exchange"}))
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 32)
    private String exchange;

    @Column(name = "asset_class", nullable = false, length = 32)
    private String assetClass;

    protected Instrument() {
    }

    public Instrument(String symbol, String exchange, String assetClass) {
        this.symbol = symbol;
        this.exchange = exchange;
        this.assetClass = assetClass;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getExchange() {
        return exchange;
    }

    public String getAssetClass() {
        return assetClass;
    }
}
