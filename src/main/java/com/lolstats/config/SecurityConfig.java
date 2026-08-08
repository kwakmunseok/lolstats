package com.lolstats.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// spring-security-crypto only (not spring-boot-starter-security) - the full starter
// auto-secures every endpoint by default, which would lock down all of Phase 1-3's public
// routes before the JWT filter (PHASE5_PLAN.md Track A Task 2) is built to carve out the
// exceptions. This class picks up the auth filter chain when that task lands.
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
