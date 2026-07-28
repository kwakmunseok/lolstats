package com.lolstats.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// Riot API routing is split in two: account-v1/match-v5 use regional routing (asia),
// summoner-v4/league-v4 use platform routing (kr). See PROJECT_PLAN.md §4.
@Configuration
public class RiotApiConfig {

    @Bean
    public RestClient riotPlatformClient(
            @Value("${riot.api.key}") String apiKey,
            @Value("${riot.api.platform-url}") String platformUrl) {
        return RestClient.builder()
                .baseUrl(platformUrl)
                .defaultHeader("X-Riot-Token", apiKey)
                .build();
    }

    @Bean
    public RestClient riotRegionalClient(
            @Value("${riot.api.key}") String apiKey,
            @Value("${riot.api.regional-url}") String regionalUrl) {
        return RestClient.builder()
                .baseUrl(regionalUrl)
                .defaultHeader("X-Riot-Token", apiKey)
                .build();
    }
}
