package com.amerritt.tradejournal.exception;

/**
 * A refresh token was unknown, expired, already rotated, or explicitly revoked.
 *
 * <p>The message is deliberately uniform across those cases: telling a caller <em>which</em> of
 * them applied would confirm that a guessed token once existed.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
