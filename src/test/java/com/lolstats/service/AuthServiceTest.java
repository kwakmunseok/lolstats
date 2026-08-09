package com.lolstats.service;

import com.lolstats.domain.RefreshToken;
import com.lolstats.domain.User;
import com.lolstats.dto.LoginRequest;
import com.lolstats.dto.SignupRequest;
import com.lolstats.dto.TokenPair;
import com.lolstats.repository.RefreshTokenRepository;
import com.lolstats.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenService jwtTokenService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, passwordEncoder, refreshTokenRepository, jwtTokenService, 14);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static SignupRequest signupRequest(String email) {
        return new SignupRequest(email, "password123", "nick", true);
    }

    private static User user(Long id, String email, String passwordHash) {
        return User.builder().id(id).email(email).passwordHash(passwordHash).nickname("nick")
                .emailVerified(true).loginFailCount(0).createdAt(Instant.now()).build();
    }

    @Test
    void signup_hashesPasswordBeforeSaving() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-value");

        service.signup(signupRequest("new@test.com"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("hashed-value", saved.getPasswordHash());
        assertNotEquals("password123", saved.getPasswordHash());
        assertEquals("new@test.com", saved.getEmail());
        assertEquals("nick", saved.getNickname());
        assertEquals(true, saved.getEmailVerified());
    }

    @Test
    void signup_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.signup(signupRequest("taken@test.com")));

        assertEquals(409, ex.getStatusCode().value());
        verify(userRepository, never()).save(any());
    }

    @Test
    void signup_raceConditionOnUniqueEmail_throwsConflictNotServerError() {
        // Two requests both pass existsByEmail (neither sees the other's row yet), then the
        // DB's unique constraint on email rejects the second save - the TOCTOU gap between the
        // check and the insert.
        when(userRepository.existsByEmail("race@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.signup(signupRequest("race@test.com")));

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void login_validCredentials_issuesTokenPairAndPersistsHashedRefreshToken() {
        User existing = user(1L, "user@test.com", "hashed-pw");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("password123", "hashed-pw")).thenReturn(true);
        when(jwtTokenService.generateAccessToken(1L)).thenReturn("access-jwt");
        when(jwtTokenService.generateOpaqueToken()).thenReturn("raw-refresh-token");
        when(jwtTokenService.hashOpaqueToken("raw-refresh-token")).thenReturn("hashed-refresh-token");

        TokenPair tokens = service.login(new LoginRequest("user@test.com", "password123"));

        assertEquals("access-jwt", tokens.accessToken());
        assertEquals("raw-refresh-token", tokens.refreshToken());
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertEquals("hashed-refresh-token", captor.getValue().getTokenHash());
        assertEquals(false, captor.getValue().getRevoked());
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User existing = user(1L, "user@test.com", "hashed-pw");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrong", "hashed-pw")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.login(new LoginRequest("user@test.com", "wrong")));

        assertEquals(401, ex.getStatusCode().value());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_unknownEmail_throwsUnauthorized() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.login(new LoginRequest("nobody@test.com", "password123")));

        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void refreshAccessToken_validToken_returnsNewAccessTokenAndSameRefreshToken() {
        User existing = user(1L, "user@test.com", "hashed-pw");
        RefreshToken stored = RefreshToken.builder().id(9L).user(existing).tokenHash("hashed-refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600)).revoked(false).build();
        when(jwtTokenService.hashOpaqueToken("raw-refresh-token")).thenReturn("hashed-refresh-token");
        when(refreshTokenRepository.findByTokenHash("hashed-refresh-token")).thenReturn(Optional.of(stored));
        when(jwtTokenService.generateAccessToken(1L)).thenReturn("new-access-jwt");

        TokenPair tokens = service.refreshAccessToken("raw-refresh-token");

        assertEquals("new-access-jwt", tokens.accessToken());
        assertEquals("raw-refresh-token", tokens.refreshToken());
    }

    @Test
    void refreshAccessToken_revokedToken_throwsUnauthorized() {
        User existing = user(1L, "user@test.com", "hashed-pw");
        RefreshToken stored = RefreshToken.builder().id(9L).user(existing).tokenHash("hashed-refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600)).revoked(true).build();
        when(jwtTokenService.hashOpaqueToken("raw-refresh-token")).thenReturn("hashed-refresh-token");
        when(refreshTokenRepository.findByTokenHash("hashed-refresh-token")).thenReturn(Optional.of(stored));

        assertThrows(ResponseStatusException.class, () -> service.refreshAccessToken("raw-refresh-token"));
    }

    @Test
    void refreshAccessToken_expiredToken_throwsUnauthorized() {
        User existing = user(1L, "user@test.com", "hashed-pw");
        RefreshToken stored = RefreshToken.builder().id(9L).user(existing).tokenHash("hashed-refresh-token")
                .expiresAt(Instant.now().minusSeconds(1)).revoked(false).build();
        when(jwtTokenService.hashOpaqueToken("raw-refresh-token")).thenReturn("hashed-refresh-token");
        when(refreshTokenRepository.findByTokenHash("hashed-refresh-token")).thenReturn(Optional.of(stored));

        assertThrows(ResponseStatusException.class, () -> service.refreshAccessToken("raw-refresh-token"));
    }

    @Test
    void refreshAccessToken_unknownToken_throwsUnauthorized() {
        when(jwtTokenService.hashOpaqueToken("bogus")).thenReturn("hashed-bogus");
        when(refreshTokenRepository.findByTokenHash("hashed-bogus")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.refreshAccessToken("bogus"));
    }

    @Test
    void logout_revokesMatchingToken() {
        RefreshToken stored = RefreshToken.builder().id(9L).tokenHash("hashed-refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600)).revoked(false).build();
        when(jwtTokenService.hashOpaqueToken("raw-refresh-token")).thenReturn("hashed-refresh-token");
        when(refreshTokenRepository.findByTokenHash("hashed-refresh-token")).thenReturn(Optional.of(stored));

        service.logout("raw-refresh-token");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertEquals(true, captor.getValue().getRevoked());
    }

    @Test
    void logout_unknownToken_doesNothing() {
        when(jwtTokenService.hashOpaqueToken("bogus")).thenReturn("hashed-bogus");
        when(refreshTokenRepository.findByTokenHash("hashed-bogus")).thenReturn(Optional.empty());

        service.logout("bogus");

        verify(refreshTokenRepository, never()).save(any());
    }
}
