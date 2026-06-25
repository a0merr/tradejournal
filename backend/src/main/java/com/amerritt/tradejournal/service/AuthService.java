package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.dto.AuthResponse;
import com.amerritt.tradejournal.dto.LoginRequest;
import com.amerritt.tradejournal.dto.RegisterRequest;
import com.amerritt.tradejournal.exception.ConflictException;
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

    public AuthService(UserRepository userRepository, AccountRepository accountRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return issueFor(user);
    }

    private AuthResponse issueFor(User user) {
        String token = jwtService.issue(user.getId(), user.getEmail());
        return AuthResponse.bearer(token, jwtService.getExpirationMinutes());
    }
}
