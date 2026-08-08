package com.lolstats.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {

    // Short secret on purpose - JwtTokenService must hash it up to HS256's 256-bit minimum
    // itself rather than requiring callers to supply a long-enough raw string.
    private final JwtTokenService service = new JwtTokenService("test-secret", 30);

    @Test
    void generateAndParseAccessToken_roundTrips() {
        String token = service.generateAccessToken(42L);

        Optional<Long> userId = service.parseUserId(token);

        assertTrue(userId.isPresent());
        assertEquals(42L, userId.get());
    }

    @Test
    void parseUserId_tamperedToken_returnsEmpty() {
        String token = service.generateAccessToken(42L);
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        assertTrue(service.parseUserId(tampered).isEmpty());
    }

    @Test
    void parseUserId_garbage_returnsEmpty() {
        assertTrue(service.parseUserId("not-a-jwt").isEmpty());
    }

    @Test
    void hashOpaqueToken_sameInput_sameHash_differentInput_differentHash() {
        String hash1 = service.hashOpaqueToken("token-a");
        String hash2 = service.hashOpaqueToken("token-a");
        String hash3 = service.hashOpaqueToken("token-b");

        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
    }

    @Test
    void generateOpaqueToken_producesDifferentValuesEachTime() {
        String a = service.generateOpaqueToken();
        String b = service.generateOpaqueToken();

        assertNotEquals(a, b);
    }
}
