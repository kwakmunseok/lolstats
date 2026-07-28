package com.lolstats.client;

import com.lolstats.client.dto.RiotAccountResponse;
import com.lolstats.client.dto.RiotLeagueEntryResponse;
import com.lolstats.client.dto.RiotMatchResponse;
import com.lolstats.client.dto.RiotSummonerResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

// 429 retry is Phase 2 (Bucket4j). Non-2xx responses here just propagate Spring's
// default RestClientResponseException subtypes (NotFound/Unauthorized/Forbidden/
// TooManyRequests, etc.) - callers catch the specific typed exception they care about.
@Component
public class RiotApiClientImpl implements RiotApiClient {

    private final RestClient platformClient;
    private final RestClient regionalClient;

    public RiotApiClientImpl(
            @Qualifier("riotPlatformClient") RestClient platformClient,
            @Qualifier("riotRegionalClient") RestClient regionalClient) {
        this.platformClient = platformClient;
        this.regionalClient = regionalClient;
    }

    @Override
    public RiotAccountResponse getAccountByRiotId(String gameName, String tagLine) {
        return regionalClient.get()
                .uri("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", gameName, tagLine)
                .retrieve()
                .body(RiotAccountResponse.class);
    }

    @Override
    public RiotSummonerResponse getSummonerByPuuid(String puuid) {
        return platformClient.get()
                .uri("/lol/summoner/v4/summoners/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(RiotSummonerResponse.class);
    }

    @Override
    public List<RiotLeagueEntryResponse> getLeagueEntriesByPuuid(String puuid) {
        return platformClient.get()
                .uri("/lol/league/v4/entries/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(new ParameterizedTypeReference<List<RiotLeagueEntryResponse>>() {
                });
    }

    @Override
    public List<String> getMatchIdsByPuuid(String puuid, int count) {
        return regionalClient.get()
                .uri("/lol/match/v5/matches/by-puuid/{puuid}/ids?count={count}", puuid, count)
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {
                });
    }

    @Override
    public RiotMatchResponse getMatchById(String matchId) {
        return regionalClient.get()
                .uri("/lol/match/v5/matches/{matchId}", matchId)
                .retrieve()
                .body(RiotMatchResponse.class);
    }
}
