package com.amerritt.tradejournal.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates every exception that escapes a controller into an RFC 7807 problem document, so
 * clients get one error shape (Content-Type {@code application/problem+json}) instead of
 * Spring's default HTML page or an ad-hoc JSON blob.
 *
 * <p>The catch-all at the bottom is the important one: without it, an unmapped exception leaks
 * a stack trace or framework internals to the caller. It logs the cause server-side and returns
 * a deliberately uninformative body.
 *
 * <p>Failures raised <em>before</em> a controller is reached — an absent or bad token — never
 * pass through here, because the security filter chain short-circuits first. Those are handled
 * by {@code ProblemDetailAuthenticationEntryPoint} and {@code ProblemDetailAccessDeniedHandler},
 * which produce the same document shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- domain ---

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), ProblemTypes.NOT_FOUND,
                "Resource not found", request);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), ProblemTypes.CONFLICT,
                "Conflict", request);
    }

    /** A constraint the application did not check first — reported without exposing the SQL. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException ex,
                                         HttpServletRequest request) {
        log.warn("Database constraint violated on {} {}", request.getMethod(),
                request.getRequestURI(), ex);
        return problem(HttpStatus.CONFLICT, "The request conflicts with existing data",
                ProblemTypes.CONFLICT, "Conflict", request);
    }

    // --- authentication / authorization ---

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex,
                                              HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage(),
                ProblemTypes.INVALID_CREDENTIALS, "Authentication failed", request);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex,
                                                   HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage(),
                ProblemTypes.INVALID_REFRESH_TOKEN, "Refresh token rejected", request);
    }

    /**
     * Any other authentication failure raised inside a controller. Without this the catch-all
     * would turn a 401 into a 500, since {@code ExceptionTranslationFilter} never sees an
     * exception that a {@code @ControllerAdvice} has already consumed.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex,
                                              HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication is required",
                ProblemTypes.UNAUTHENTICATED, "Unauthenticated", request);
    }

    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ProblemDetail handleDenied(Exception ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", ProblemTypes.ACCESS_DENIED,
                "Access denied", request);
    }

    // --- request validation ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex,
                                          HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "is invalid" : fe.getDefaultMessage()))
                .toList();
        String detail = errors.stream()
                .map(e -> e.get("field") + ": " + e.get("message"))
                .collect(Collectors.joining("; "));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST,
                detail.isEmpty() ? "Validation failed" : detail,
                ProblemTypes.VALIDATION_FAILED, "Validation failed", request);
        // Field-level detail as structured data, so clients can highlight inputs
        // instead of parsing the prose in `detail`.
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Validation on query/path parameters, which does not go through binding results. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex,
                                                   HttpServletRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, detail.isEmpty() ? "Validation failed" : detail,
                ProblemTypes.VALIDATION_FAILED, "Validation failed", request);
    }

    /** Unparseable JSON, or a value that cannot become the target type (bad enum, bad number). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex,
                                          HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Request body is missing or not valid JSON",
                ProblemTypes.MALFORMED_REQUEST, "Malformed request", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' has the wrong type",
                ProblemTypes.MALFORMED_REQUEST, "Malformed request", request);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    public ProblemDetail handleMissingParam(Exception ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(),
                ProblemTypes.MALFORMED_REQUEST, "Malformed request", request);
    }

    /** CSV import beyond {@code spring.servlet.multipart.max-file-size}. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex,
                                        HttpServletRequest request) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the size limit",
                ProblemTypes.PAYLOAD_TOO_LARGE, "Payload too large", request);
    }

    /** An unmapped URL. Kept out of the catch-all so it is not logged as a server fault. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex,
                                          HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "No handler for " + request.getRequestURI(),
                ProblemTypes.NOT_FOUND, "Resource not found", request);
    }

    // --- catch-all ---

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error",
                ProblemTypes.INTERNAL_ERROR, "Internal server error", request);
    }

    private static ProblemDetail problem(HttpStatus status, String detail, URI type,
                                         String title, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                detail == null || detail.isBlank() ? title : detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ProblemTypes.decorate(problem, type, title);
    }
}
