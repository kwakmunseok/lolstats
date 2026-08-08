package com.lolstats.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

// Plain double-submit-cookie check, not Spring Security's built-in CsrfFilter (PHASE5_PLAN.md
// Task 2 tried CookieCsrfTokenRepository + forcing eager token resolution and hit an unstable
// XSRF-TOKEN cookie in live testing, traced to its BREACH-protection XOR encoding layer - more
// machinery than this app needs). AuthController issues a random, JS-readable XSRF-TOKEN cookie
// on login/refresh; this filter just requires the same value to come back as a header on
// state-changing requests to the one path that mutates real per-user state. A cross-site
// attacker can trigger the request but can't read the cookie to put its value in the header
// (same-origin policy), so a mismatch means the request didn't originate from this app's own JS.
public class CsrfDoubleSubmitFilter extends OncePerRequestFilter {

    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String PROTECTED_PREFIX = "/api/users/me/";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (UNSAFE_METHODS.contains(request.getMethod()) && request.getRequestURI().startsWith(PROTECTED_PREFIX)) {
            String cookieValue = CookieUtils.extract(request, CSRF_COOKIE).orElse(null);
            String headerValue = request.getHeader(CSRF_HEADER);
            if (cookieValue == null || !cookieValue.equals(headerValue)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF token missing or invalid");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
