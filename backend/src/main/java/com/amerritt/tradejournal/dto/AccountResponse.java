package com.amerritt.tradejournal.dto;

import com.amerritt.tradejournal.model.Account;

import java.time.OffsetDateTime;

public record AccountResponse(
        Long id,
        String broker,
        String baseCurrency,
        OffsetDateTime createdAt) {

    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getBroker(), a.getBaseCurrency(), a.getCreatedAt());
    }
}
