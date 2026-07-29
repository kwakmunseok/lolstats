package com.lolstats.client;

import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.time.Duration;

// Riot API routing is split in two: account-v1/match-v5 use regional routing (asia),
// summoner-v4/league-v4 use platform routing (kr). See PROJECT_PLAN.md §4.
@Configuration
public class RiotApiConfig {

    // Riot's global limit: 20 req/s + 100 req/2min (PROJECT_PLAN.md §4 실제 호출 횟수 계산).
    // One bucket shared by both clients below - it's a global cap, not per-client
    // (PHASE2_PLAN.md Task 1 결정 사항: RestClient 공통 인터셉터가 버킷 하나를 공유).
    @Bean
    public Bucket riotApiRateLimitBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(20).refillGreedy(20, Duration.ofSeconds(1)))
                .addLimit(limit -> limit.capacity(100).refillGreedy(100, Duration.ofMinutes(2)))
                .build();
    }

    @Bean
    public RestClient riotPlatformClient(
            @Value("${riot.api.key}") String apiKey,
            @Value("${riot.api.platform-url}") String platformUrl,
            Bucket riotApiRateLimitBucket) {
        return RestClient.builder()
                .baseUrl(platformUrl)
                .defaultHeader("X-Riot-Token", apiKey)
                .requestInterceptor(rateLimiting(riotApiRateLimitBucket))
                .build();
    }

    @Bean
    public RestClient riotRegionalClient(
            @Value("${riot.api.key}") String apiKey,
            @Value("${riot.api.regional-url}") String regionalUrl,
            Bucket riotApiRateLimitBucket) {
        return RestClient.builder()
                .baseUrl(regionalUrl)
                .defaultHeader("X-Riot-Token", apiKey)
                .requestInterceptor(rateLimiting(riotApiRateLimitBucket))
                .build();
    }

    // Blocks the calling thread until a token frees up rather than rejecting outright - this
    // is deliberately the simplest thing that works for Task 1. Phase 2 Task 3's background
    // queue is what turns "a request thread blocks" into "a queue waits" (PHASE2_PLAN.md).
    private ClientHttpRequestInterceptor rateLimiting(Bucket bucket) {
        return (request, body, execution) -> {
            bucket.asBlocking().consumeUninterruptibly(1);
            return execution.execute(request, body);
        };
    }
}
