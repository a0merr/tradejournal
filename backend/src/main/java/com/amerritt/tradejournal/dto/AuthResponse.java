package com.amerritt.tradejournal.dto;

/**
 * A freshly minted session: a short-lived access JWT plus the opaque refresh token that buys
 * the next one. The refresh token is single-use — every call to {@code /api/auth/refresh}
 * returns a new one, and the old one is dead on arrival.
 */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMinutes,
        String refreshToken,
        long refreshExpiresInMinutes) {

    public static AuthResponse bearer(String token, long expiresInMinutes,
                                      String refreshToken, long refreshExpiresInMinutes) {
        return new AuthResponse(token, "Bearer", expiresInMinutes,
                refreshToken, refreshExpiresInMinutes);
    }
}
