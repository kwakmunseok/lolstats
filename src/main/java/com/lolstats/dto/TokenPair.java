package com.lolstats.dto;

// Carries the raw access/refresh tokens from AuthService to the controller, which puts them in
// httpOnly cookies (PROJECT_PLAN.md §6) - never serialized directly as a JSON response body.
public record TokenPair(String accessToken, String refreshToken) {
}
