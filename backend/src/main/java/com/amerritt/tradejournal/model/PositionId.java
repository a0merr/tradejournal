package com.amerritt.tradejournal.model;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for the read-only {@code positions} view: one row per (account, instrument). */
public class PositionId implements Serializable {

    private Long accountId;
    private Long instrumentId;

    public PositionId() {
    }

    public PositionId(Long accountId, Long instrumentId) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PositionId that)) {
            return false;
        }
        return Objects.equals(accountId, that.accountId)
                && Objects.equals(instrumentId, that.instrumentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, instrumentId);
    }
}
