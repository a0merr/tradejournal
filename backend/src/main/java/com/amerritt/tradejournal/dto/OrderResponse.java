package com.amerritt.tradejournal.dto;

import com.amerritt.tradejournal.model.OrderEntity;
import com.amerritt.tradejournal.model.OrderStatus;
import com.amerritt.tradejournal.model.OrderType;
import com.amerritt.tradejournal.model.Side;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long accountId,
        String symbol,
        String exchange,
        Side side,
        OrderType type,
        BigDecimal quantity,
        OrderStatus status,
        OffsetDateTime createdAt,
        List<FillResponse> fills) {

    public static OrderResponse from(OrderEntity o, List<FillResponse> fills) {
        return new OrderResponse(
                o.getId(),
                o.getAccount().getId(),
                o.getInstrument().getSymbol(),
                o.getInstrument().getExchange(),
                o.getSide(),
                o.getType(),
                o.getQuantity(),
                o.getStatus(),
                o.getCreatedAt(),
                fills);
    }
}
