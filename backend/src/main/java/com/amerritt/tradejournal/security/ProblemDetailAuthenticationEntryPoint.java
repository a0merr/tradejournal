package com.amerritt.tradejournal.security;

import com.amerritt.tradejournal.exception.ProblemTypes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * The 401 path: no token, a malformed one, or one that no longer verifies.
 *
 * <p>An expired token is called out with its own problem {@code type} so a client can tell
 * "refresh and retry" apart from "start over at login" without guessing from the status code.
 * {@link JwtAuthFilter} sets the marker attribute this reads.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailWriter writer;

    public ProblemDetailAuthenticationEntryPoint(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean expired = Boolean.TRUE.equals(request.getAttribute(JwtAuthFilter.EXPIRED_ATTRIBUTE));
        // WWW-Authenticate is required by RFC 9110 on a 401 and tells the client which scheme to use.
        response.setHeader("WWW-Authenticate", expired
                ? "Bearer error=\"invalid_token\", error_description=\"The access token expired\""
                : "Bearer");

        if (expired) {
            writer.write(request, response, HttpStatus.UNAUTHORIZED, ProblemTypes.TOKEN_EXPIRED,
                    "Access token expired", "The access token has expired; refresh it and retry");
        } else {
            writer.write(request, response, HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHENTICATED,
                    "Unauthenticated", "A valid bearer token is required");
        }
    }
}
