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
                // CSRF enforcement is deferred to Task 3 (PHASE5_PLAN.md), not built here.
                // Right now zero endpoints exist that a CSRF attack could meaningfully abuse:
                // signup/login have no prior session to forge a request against, the existing
                // /api/summoners/** POST (refresh button, Phase 1-3) changes no personal state,
                // and logout/refresh-token give an attacker nothing they don't already have with
                // the victim's stolen cookie. Task 3 adds /api/users/me/** (favorites) - the
                // first endpoint that actually mutates per-user state - and wires the
                // double-submit cookie token then, alongside the screens that carry it.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/users/me/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenService), UsernamePasswordAuthenticationFilter.class)
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
