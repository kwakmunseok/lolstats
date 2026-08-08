package com.lolstats.controller;

import com.lolstats.domain.User;
import com.lolstats.dto.LoginRequest;
import com.lolstats.dto.SignupRequest;
import com.lolstats.dto.SignupResponse;
import com.lolstats.dto.TokenPair;
import com.lolstats.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final boolean cookieSecure;
    private final long accessTokenMinutes;
    private final long refreshTokenDays;

    public AuthController(
            AuthService authService,
            @Value("${app.jwt.cookie-secure}") boolean cookieSecure,
            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes,
            @Value("${app.jwt.refresh-token-days}") long refreshTokenDays) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenDays = refreshTokenDays;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        User user = authService.signup(request);
        return SignupResponse.from(user);
    }

    @PostMapping("/login")
    public void login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        setAuthCookies(response, authService.login(request));
    }

    @PostMapping("/logout")
    public void logout(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        clearAuthCookies(response);
    }

    @PostMapping("/refresh")
    public void refresh(@CookieValue(REFRESH_COOKIE) String refreshToken, HttpServletResponse response) {
        setAuthCookies(response, authService.refreshAccessToken(refreshToken));
    }

    private void setAuthCookies(HttpServletResponse response, TokenPair tokens) {
        addCookie(response, ACCESS_COOKIE, tokens.accessToken(), accessTokenMinutes * 60);
        addCookie(response, REFRESH_COOKIE, tokens.refreshToken(), refreshTokenDays * 24 * 60 * 60);
    }

    private void clearAuthCookies(HttpServletResponse response) {
        addCookie(response, ACCESS_COOKIE, "", 0);
        addCookie(response, REFRESH_COOKIE, "", 0);
    }

    // ResponseCookie (not jakarta.servlet.http.Cookie) - the Servlet Cookie API has no SameSite
    // support, and PROJECT_PLAN.md §6 commits to SameSite=Lax as part of the CSRF defense.
    private void addCookie(HttpServletResponse response, String name, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
