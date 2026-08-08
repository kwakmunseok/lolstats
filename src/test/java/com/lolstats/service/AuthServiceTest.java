package com.lolstats.service;

import com.lolstats.domain.User;
import com.lolstats.dto.SignupRequest;
import com.lolstats.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

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

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, passwordEncoder);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static SignupRequest request(String email) {
        return new SignupRequest(email, "password123", "nick", true);
    }

    @Test
    void signup_hashesPasswordBeforeSaving() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-value");

        service.signup(request("new@test.com"));

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
                () -> service.signup(request("taken@test.com")));

        assertEquals(409, ex.getStatusCode().value());
        verify(userRepository, never()).save(any());
    }
}
