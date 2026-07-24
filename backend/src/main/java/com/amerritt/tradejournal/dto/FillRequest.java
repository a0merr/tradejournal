package com.amerritt.tradejournal.dto;

import com.amerritt.tradejournal.model.OrderType;
import com.amerritt.tradejournal.model.Side;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * The trading bot's ingest payload. One fill, self-describing: it names the
 * account and instrument, and the service resolves/creates the instrument and
 * the owning order before recording the execution.
 *
 * <p>{@code externalId} is the broker's execution id. Supplying it makes ingestion idempotent:
 * a retry after a timeout returns the fill already recorded instead of booking it twice. It is
 * optional only so existing callers keep working — any bot that retries should send it.
 */
public record FillRequest(
        @NotNull @Positive Long accountId,
        @NotBlank String symbol,
        @NotBlank String exchange,
        @NotBlank String assetClass,
        @NotNull Side side,
        @NotNull OrderType type,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.0") BigDecimal price,
        @DecimalMin(value = "0.0") BigDecimal fee,
        OffsetDateTime filledAt,
        @Size(max = 64) String externalId) {
}
