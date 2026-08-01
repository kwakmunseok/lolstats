package com.lolstats.client;

import com.lolstats.client.dto.RiotAccountResponse;
import com.lolstats.client.dto.RiotLeagueEntryResponse;
import com.lolstats.client.dto.RiotLeagueItemResponse;
import com.lolstats.client.dto.RiotLeagueListResponse;
import com.lolstats.client.dto.RiotLeagueSeedEntryResponse;
import com.lolstats.client.dto.RiotMatchResponse;
import com.lolstats.client.dto.RiotSummonerResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Supplier;

// Non-2xx responses propagate Spring's typed RestClientResponseException subtypes
// (NotFound/Unauthorized/Forbidden/TooManyRequests, etc.) - callers catch the specific
// exception they care about. TooManyRequests gets a bounded retry here (Phase 2 Task 2);
// Unauthorized/Forbidden don't (a rejected key won't fix itself by waiting).
@Slf4j
@Component
public class RiotApiClientImpl implements RiotApiClient {

    // Riot rarely asks for more than a few seconds; 3 attempts bounds worst-case wait to a
    // handful of Retry-After windows instead of retrying forever (PHASE2_PLAN.md Task 2).
    private static final int MAX_RETRIES = 3;

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
        return withRetry(() -> regionalClient.get()
                .uri("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", gameName, tagLine)
                .retrieve()
                .body(RiotAccountResponse.class));
    }

    @Override
    public RiotAccountResponse getAccountByPuuid(String puuid) {
        return withRetry(() -> regionalClient.get()
                .uri("/riot/account/v1/accounts/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(RiotAccountResponse.class));
    }

    @Override
    public RiotSummonerResponse getSummonerByPuuid(String puuid) {
        return withRetry(() -> platformClient.get()
                .uri("/lol/summoner/v4/summoners/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(RiotSummonerResponse.class));
    }

    @Override
    public List<RiotLeagueEntryResponse> getLeagueEntriesByPuuid(String puuid) {
        return withRetry(() -> platformClient.get()
                .uri("/lol/league/v4/entries/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(new ParameterizedTypeReference<List<RiotLeagueEntryResponse>>() {
                }));
    }

    @Override
    public List<RiotLeagueSeedEntryResponse> getChallengerLeague(String queue) {
        return getApexLeague("/lol/league/v4/challengerleagues/by-queue/{queue}", queue);
    }

    @Override
    public List<RiotLeagueSeedEntryResponse> getGrandmasterLeague(String queue) {
        return getApexLeague("/lol/league/v4/grandmasterleagues/by-queue/{queue}", queue);
    }

    @Override
    public List<RiotLeagueSeedEntryResponse> getMasterLeague(String queue) {
        return getApexLeague("/lol/league/v4/masterleagues/by-queue/{queue}", queue);
    }

    private List<RiotLeagueSeedEntryResponse> getApexLeague(String uriTemplate, String queue) {
        RiotLeagueListResponse response = withRetry(() -> platformClient.get()
                .uri(uriTemplate, queue)
                .retrieve()
                .body(RiotLeagueListResponse.class));
        return response.entries().stream()
                .map(item -> new RiotLeagueSeedEntryResponse(
                        item.puuid(), response.tier(), item.rank(), item.leaguePoints(), item.wins(), item.losses()))
                .toList();
    }

    @Override
    public List<RiotLeagueSeedEntryResponse> getLeagueEntries(String queue, String tier, String division, int page) {
        return withRetry(() -> platformClient.get()
                .uri("/lol/league/v4/entries/{queue}/{tier}/{division}?page={page}", queue, tier, division, page)
                .retrieve()
                .body(new ParameterizedTypeReference<List<RiotLeagueSeedEntryResponse>>() {
                }));
    }

    @Override
    public List<String> getMatchIdsByPuuid(String puuid, int count) {
        return withRetry(() -> regionalClient.get()
                .uri("/lol/match/v5/matches/by-puuid/{puuid}/ids?count={count}", puuid, count)
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {
                }));
    }

    @Override
    public RiotMatchResponse getMatchById(String matchId) {
        return withRetry(() -> regionalClient.get()
                .uri("/lol/match/v5/matches/{matchId}", matchId)
                .retrieve()
                .body(RiotMatchResponse.class));
    }

    private <T> T withRetry(Supplier<T> call) {
        int attempt = 0;
        while (true) {
            try {
                return call.get();
            } catch (HttpClientErrorException.TooManyRequests e) {
                attempt++;
                if (attempt > MAX_RETRIES) {
                    throw e;
                }
                log.warn("Riot API 429, retrying ({}/{}) after {}ms", attempt, MAX_RETRIES, retryAfterMillis(e));
                sleepUninterruptibly(retryAfterMillis(e));
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                log.warn("Riot API rejected the key ({}) - Dev Key may have expired", e.getStatusCode());
                throw e;
            }
        }
    }

    private long retryAfterMillis(HttpClientErrorException.TooManyRequests e) {
        String header = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null;
        long seconds = header != null ? Long.parseLong(header) : 1;
        return seconds * 1000;
    }

    private void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
