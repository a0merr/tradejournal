package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.exception.InvalidRefreshTokenException;
import com.amerritt.tradejournal.model.RefreshToken;
import com.amerritt.tradejournal.model.User;
import com.amerritt.tradejournal.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues, rotates, and revokes refresh tokens.
 *
 * <p>Rotation scheme: every successful refresh spends the presented token and mints a
 * replacement in the same family, so a token is valid for exactly one use. If a spent token is
 * presented again, either the client or an attacker holds a copy that the other one already
 * used — indistinguishable from here — so the entire family is revoked and both parties are
 * forced back through login. That is what makes a stolen refresh token survivable: the theft
 * becomes detectable the moment either side uses it.
 *
 * <p>Tokens are 256 bits from {@link SecureRandom} and opaque (no structure to forge). Only the
 * SHA-256 is stored. A plain hash is sufficient here where it is not for passwords: the input is
 * full-entropy random, so there is nothing to brute-force or precompute.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, Clock clock,
                               @Value("${tradejournal.jwt.refresh-expiration-days}") long refreshDays) {
        this.repository = repository;
        this.clock = clock;
        this.ttl = Duration.ofDays(refreshDays);
    }

    /**
     * Who the rotated token belongs to, plus its single-use replacement. Carries the identity
     * as plain values rather than the entity, so callers cannot trip over a lazy proxy once the
     * transaction has closed.
     */
    public record Rotation(Long userId, String email, String refreshToken) {
    }

    public Duration getTtl() {
        return ttl;
    }

    /** Starts a new token family (a new login). */
    @Transactional
    public String issue(User user) {
        return persist(user, UUID.randomUUID());
    }

    /**
     * Spends {@code rawToken} and returns its replacement.
     *
     * <p>{@code noRollbackFor} is load-bearing: the reuse and expiry paths revoke rows and
     * <em>then</em> reject the caller. A default rollback would undo the revocation the
     * rejection exists to record.
     *
     * @throws InvalidRefreshTokenException if the token is unknown, expired, or already spent
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        OffsetDateTime now = now();
        RefreshToken current = repository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (current.isRevoked()) {
            // Reuse of a spent token: the family is compromised, so no member survives.
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {} — revoked {} token(s) in family {}",
                    current.getUser().getId(), revoked, current.getFamilyId());
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpired(now)) {
            current.revoke(now);
            throw new InvalidRefreshTokenException();
        }

        current.revoke(now);
        User user = current.getUser();
        return new Rotation(user.getId(), user.getEmail(),
                persist(user, current.getFamilyId()));
    }

    /** Logout. Unknown or already-revoked tokens are a no-op, so the call is idempotent. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHashForUpdate(hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), now()));
    }

    /** Logout everywhere — e.g. after a password change. */
    @Transactional
    public int revokeAllForUser(Long userId) {
        return repository.revokeAllForUser(userId, now());
    }

    /** Housekeeping: expired rows carry no authority and only cost storage. */
    @Transactional
    public int purgeExpired() {
        return repository.deleteExpiredBefore(now());
    }

    private String persist(User user, UUID familyId) {
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        repository.save(new RefreshToken(user, hash(token), familyId, now().plus(ttl)));
        return token;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by every JVM", ex);
        }
    }
}
