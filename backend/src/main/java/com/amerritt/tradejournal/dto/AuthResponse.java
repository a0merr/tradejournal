package com.amerritt.tradejournal.dto;

public record AuthResponse(String token, String tokenType, long expiresInMinutes) {

    public static AuthResponse bearer(String token, long expiresInMinutes) {
        return new AuthResponse(token, "Bearer", expiresInMinutes);
    }
}
