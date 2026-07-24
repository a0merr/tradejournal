package com.amerritt.tradejournal.exception;

import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;

/**
 * Stable {@code type} URIs for the RFC 7807 problem responses this API emits.
 *
 * <p>URNs rather than {@code https://} URLs: the field's contract is to be a stable identifier
 * clients can branch on, and a URN cannot rot into a broken link or invite a runtime fetch.
 */
public final class ProblemTypes {

    private static final String PREFIX = "urn:tradejournal:problem:";

    public static final URI VALIDATION_FAILED = URI.create(PREFIX + "validation-failed");
    public static final URI MALFORMED_REQUEST = URI.create(PREFIX + "malformed-request");
    public static final URI NOT_FOUND = URI.create(PREFIX + "not-found");
    public static final URI CONFLICT = URI.create(PREFIX + "conflict");
    public static final URI INVALID_CREDENTIALS = URI.create(PREFIX + "invalid-credentials");
    public static final URI UNAUTHENTICATED = URI.create(PREFIX + "unauthenticated");
    public static final URI TOKEN_EXPIRED = URI.create(PREFIX + "token-expired");
    public static final URI INVALID_REFRESH_TOKEN = URI.create(PREFIX + "invalid-refresh-token");
    public static final URI ACCESS_DENIED = URI.create(PREFIX + "access-denied");
    public static final URI PAYLOAD_TOO_LARGE = URI.create(PREFIX + "payload-too-large");
    public static final URI RATE_LIMITED = URI.create(PREFIX + "rate-limited");
    public static final URI INTERNAL_ERROR = URI.create(PREFIX + "internal-error");

    private ProblemTypes() {
    }

    /**
     * Stamps the fields every response in this API carries beyond the RFC minimum: a machine
     * -readable {@code type}, a human {@code title}, and the instant the failure was observed
     * (which is what makes a user-reported error findable in the logs).
     */
    public static ProblemDetail decorate(ProblemDetail problem, URI type, String title) {
        problem.setType(type);
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
