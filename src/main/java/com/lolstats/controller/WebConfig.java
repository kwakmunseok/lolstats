package com.lolstats.controller;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PerIpRateLimitInterceptor perIpRateLimitInterceptor;

    public WebConfig(PerIpRateLimitInterceptor perIpRateLimitInterceptor) {
        this.perIpRateLimitInterceptor = perIpRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Only the two endpoints that trigger Riot API calls (PHASE2_PLAN.md Task 6) -
        // autocomplete/popular/matches-listing are DB/Redis-only and don't spend Riot's budget.
        registry.addInterceptor(perIpRateLimitInterceptor)
                .addPathPatterns("/api/summoners/riot-id/**", "/api/summoners/*/refresh");
    }
}
