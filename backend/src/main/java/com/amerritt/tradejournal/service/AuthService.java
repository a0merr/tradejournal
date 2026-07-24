package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.dto.AuthResponse;
import com.amerritt.tradejournal.dto.LoginRequest;
import com.amerritt.tradejournal.dto.RegisterRequest;
import com.amerritt.tradejournal.exception.ConflictException;
import com.amerritt.tradejournal.exception.InvalidRefreshTokenException;
import com.amerritt.tradejournal.model.Account;
import com.amerritt.tradejournal.model.User;
import com.amerritt.tradejournal.repository.AccountRepository;
import com.amerritt.tradejournal.repository.UserRepository;
import com.amerritt.tradejournal.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, AccountRepository accountRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = userRepository.save(new User(email, passwordEncoder.encode(req.password())));
        // Give every new user a default account so they can ingest fills immediately.
        accountRepository.save(new Account(user, "DefaultBroker", "USD"));
        return issueFor(user);
    }

    // Not readOnly: a successful login writes a refresh-token row.
    @Transactional
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return issueFor(user);
    }

    /**
     * Trades a refresh token for a new access token and a new refresh token, invalidating the
     * one presented. Deliberately does not require a valid access token — the whole point is to
     * be callable once the access token has expired.
     *
     * <p>The {@code noRollbackFor} must match {@link RefreshTokenService#rotate}: this method
     * joins that transaction, so without it the outer rollback rule would discard the
     * reuse-detection revocation on the way out.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        String access = jwtService.issue(rotation.userId(), rotation.email());
        return bearer(access, rotation.refreshToken());
    }

    /** Ends the session chain the token belongs to. Idempotent. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse issueFor(User user) {
        return bearer(jwtService.issue(user.getId(), user.getEmail()),
                refreshTokenService.issue(user));
    }

    private AuthResponse bearer(String accessToken, String refreshToken) {
        return AuthResponse.bearer(accessToken, jwtService.getExpirationMinutes(),
                refreshToken, refreshTokenService.getTtl().toMinutes());
    }
}
