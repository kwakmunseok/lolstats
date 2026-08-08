package com.lolstats.service;

import com.lolstats.domain.RefreshToken;
import com.lolstats.domain.User;
import com.lolstats.dto.LoginRequest;
import com.lolstats.dto.SignupRequest;
import com.lolstats.dto.TokenPair;
import com.lolstats.repository.RefreshTokenRepository;
import com.lolstats.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;
    private final long refreshTokenDays;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenService jwtTokenService,
            @Value("${app.jwt.refresh-token-days}") long refreshTokenDays) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenDays = refreshTokenDays;
    }

    public User signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                // Email verification is deferred (PHASE5_PLAN.md §1) - accounts start active.
                .emailVerified(true)
                .loginFailCount(0)
                .createdAt(Instant.now())
                .build();

        return userRepository.save(user);
    }

    public TokenPair login(LoginRequest request) {
        // Same error for "no such email" and "wrong password" - distinguishing them would let
        // an attacker enumerate registered emails via the login endpoint.
        User user = userRepository.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"));
        return issueTokenPair(user);
    }

    // No rotation (PHASE5_PLAN.md §5 미결 기본값) - the same refresh token stays valid until
    // its own expiry rather than being replaced on every use.
    public TokenPair refreshAccessToken(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(jwtTokenService.hashOpaqueToken(rawRefreshToken))
                .filter(t -> !t.getRevoked() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "다시 로그인해주세요"));
        String accessToken = jwtTokenService.generateAccessToken(stored.getUser().getId());
        return new TokenPair(accessToken, rawRefreshToken);
    }

    // Unknown token (already logged out, expired and pruned, or forged) is a no-op rather than
    // an error - logout is idempotent from the caller's point of view either way.
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(jwtTokenService.hashOpaqueToken(rawRefreshToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private TokenPair issueTokenPair(User user) {
        String accessToken = jwtTokenService.generateAccessToken(user.getId());
        String rawRefreshToken = jwtTokenService.generateOpaqueToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(jwtTokenService.hashOpaqueToken(rawRefreshToken))
                .expiresAt(Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS))
                .revoked(false)
                .build());
        return new TokenPair(accessToken, rawRefreshToken);
    }
}
