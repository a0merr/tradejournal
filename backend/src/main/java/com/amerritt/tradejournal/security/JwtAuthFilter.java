package com.amerritt.tradejournal.security;

import com.amerritt.tradejournal.model.User;
import com.amerritt.tradejournal.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    /**
     * Set when the presented token parsed but had passed its expiry. Read by
     * {@link ProblemDetailAuthenticationEntryPoint} to distinguish "refresh and retry" from
     * "authenticate from scratch" — the filter cannot answer the request itself, since a public
     * endpoint with a stale token must still succeed.
     */
    static final String EXPIRED_ATTRIBUTE = JwtAuthFilter.class.getName() + ".expired";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIX.length());
            try {
                Long userId = jwtService.parseUserId(token);
                Optional<User> user = userRepository.findById(userId);
                if (user.isPresent()) {
                    AuthPrincipal principal = new AuthPrincipal(userId, user.get().getEmail());
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, AuthorityUtils.NO_AUTHORITIES);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (ExpiredJwtException ex) {
                // Signature was good, the clock ran out: the client should refresh, not re-login.
                request.setAttribute(EXPIRED_ATTRIBUTE, Boolean.TRUE);
                SecurityContextHolder.clearContext();
            } catch (JwtException | IllegalArgumentException ex) {
                // Invalid token: leave context unauthenticated -> 401 downstream.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
