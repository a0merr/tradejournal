package com.amerritt.tradejournal.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for {@code /api/auth/refresh} and {@code /api/auth/logout}. */
public record RefreshRequest(@NotBlank String refreshToken) {
}
