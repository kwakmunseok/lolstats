package com.lolstats.config;

import com.lolstats.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Reads the access_token cookie set by AuthController and, if it's a valid unexpired JWT,
// populates the SecurityContext with the userId as principal - the only thing downstream
// controllers (e.g. Task 3's /api/users/me/*) need. No UserDetailsService/roles: this app has
// no authorities to check, only "is this a valid session for user X".
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ACCESS_COOKIE = "access_token";

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CookieUtils.extract(request, ACCESS_COOKIE)
                .flatMap(jwtTokenService::parseUserId)
                .ifPresent(userId -> SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of())));
        filterChain.doFilter(request, response);
    }
}
