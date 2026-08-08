package com.lolstats.config;

import com.lolstats.service.JwtTokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Cookie-based JWT auth (PROJECT_PLAN.md §6) instead of Spring Security's default session/form
// login - stateless, no UserDetailsService (JwtAuthenticationFilter sets the userId principal
// directly from the token).
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenService jwtTokenService) throws Exception {
        http
                // Spring Security's own CsrfFilter is disabled - Task 2 hit an unstable
                // XSRF-TOKEN cookie with CookieCsrfTokenRepository (its BREACH-protection XOR
                // encoding layer). CsrfDoubleSubmitFilter below is a plain double-submit-cookie
                // check instead, scoped to /api/users/me/** - the only endpoints (added this
                // task) that mutate real per-user state. Nothing else needs it: signup/login
                // have no prior session to forge a request against, and the existing
                // /api/summoners/** POST (refresh button, Phase 1-3) changes no personal state.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/users/me/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenService), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfDoubleSubmitFilter(), JwtAuthenticationFilter.class)
                // Task 4 uses @AuthenticationPrincipal Long userId on a permitAll route (search
                // history is recorded only when the caller happens to be logged in). Spring
                // Security's default AnonymousAuthenticationFilter would otherwise leave a
                // non-null Authentication with a String principal ("anonymousUser") in the
                // context for every unauthenticated request, and casting that to Long throws.
                // Disabling it means "not authenticated" is simply a null Authentication, which
                // @AuthenticationPrincipal already resolves to null - no cast attempted. permitAll
                // routes don't need an Authentication object to exist either way, and the
                // .authenticated() check on /api/users/me/** already treated anonymous as
                // "not authenticated" before this, so nothing else changes.
                .anonymous(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // No login page exists (Thymeleaf login.html lands in Task 5) - without this,
                // Spring Security's default entry point for an unauthenticated request to a
                // protected route falls back to a redirect/403 that varies by version rather
                // than the plain 401 a JSON API should return.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)));
        return http.build();
    }
}
