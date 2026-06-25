package com.amerritt.tradejournal.security;

/** Authenticated principal carried on the SecurityContext. */
public record AuthPrincipal(Long userId, String email) {
}
