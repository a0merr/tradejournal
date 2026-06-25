package com.amerritt.tradejournal.dto;

import com.amerritt.tradejournal.model.Fill;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FillResponse(
        Long id,
        Long orderId,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fee,
        OffsetDateTime filledAt) {

    public static FillResponse from(Fill f) {
        return new FillResponse(
                f.getId(),
                f.getOrder().getId(),
                f.getQuantity(),
                f.getPrice(),
                f.getFee(),
                f.getFilledAt());
    }
}
