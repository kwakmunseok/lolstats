package com.lolstats.service;

import com.lolstats.client.RiotApiClient;
import com.lolstats.client.dto.RiotAccountResponse;
import com.lolstats.client.dto.RiotLeagueEntryResponse;
import com.lolstats.client.dto.RiotSummonerResponse;
import com.lolstats.domain.SearchCount;
import com.lolstats.domain.Summoner;
import com.lolstats.repository.SearchCountRepository;
import com.lolstats.repository.SummonerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SummonerService {

    private static final String SOLO_QUEUE = "RANKED_SOLO_5x5";

    // [전적 갱신] cooldown - short on purpose (unlike the 10min search cache): it's an
    // explicit user action meant to be usable fairly often, just not spammable
    // (PHASE2_PLAN.md Task 4 결정 사항).
    private static final String COOLDOWN_PREFIX = "cooldown:";
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);

    private final SummonerRepository summonerRepository;
    private final SearchCountRepository searchCountRepository;
    private final RiotApiClient riotApiClient;
    private final StringRedisTemplate redisTemplate;
    private final long ttlMinutes;

    public SummonerService(
            SummonerRepository summonerRepository,
            SearchCountRepository searchCountRepository,
            RiotApiClient riotApiClient,
            StringRedisTemplate redisTemplate,
            @Value("${app.cache.summoner-ttl-minutes}") long ttlMinutes) {
        this.summonerRepository = summonerRepository;
        this.searchCountRepository = searchCountRepository;
        this.riotApiClient = riotApiClient;
        this.redisTemplate = redisTemplate;
        this.ttlMinutes = ttlMinutes;
    }

    public Summoner findOrFetch(String gameName, String tagLine) {
        Optional<Summoner> cached = summonerRepository.findByGameNameAndTagLine(gameName, tagLine).stream()
                .max(Comparator.comparing(Summoner::getUpdatedAt));

        Summoner summoner = cached.filter(this::isFresh)
                .orElseGet(() -> fetchAndUpsert(gameName, tagLine));

        recordSearch(summoner);
        return summoner;
    }

    // TTL 무관 강제 갱신 ([전적 갱신] 버튼 - PHASE2_PLAN.md Task 4). Cooldown gating and
    // match re-collection are the caller's job (SummonerController), same split as
    // findOrFetch()/MatchService.planCollection() for a normal search.
    public Summoner refresh(Long summonerId) {
        Summoner existing = summonerRepository.findById(summonerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "summoner not found: " + summonerId));
        Summoner refreshed = fetchAndUpsert(existing.getGameName(), existing.getTagLine());
        recordSearch(refreshed);
        return refreshed;
    }

    public boolean isRefreshCoolingDown(Long summonerId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_PREFIX + summonerId));
    }

    // Called only after a refresh (including its match re-collection enqueue) has actually
    // gone through - a failed refresh shouldn't cost the user the cooldown window
    // (PROJECT_PLAN.md §4 Phase 2: "쿨다운은 큐잉 성공 후에 설정한다").
    public void startRefreshCooldown(Long summonerId) {
        redisTemplate.opsForValue().set(COOLDOWN_PREFIX + summonerId, "1", COOLDOWN_TTL);
    }

    private boolean isFresh(Summoner summoner) {
        return summoner.getUpdatedAt() != null
                && summoner.getUpdatedAt().isAfter(Instant.now().minus(ttlMinutes, ChronoUnit.MINUTES));
    }

    private Summoner fetchAndUpsert(String gameName, String tagLine) {
        RiotAccountResponse account = riotApiClient.getAccountByRiotId(gameName, tagLine);

        // A Riot ID's puuid can change hands, so the name we searched by isn't a safe
        // upsert key - always resolve against the puuid Riot just gave us (PROJECT_PLAN.md §6).
        Summoner summoner = summonerRepository.findByPuuid(account.puuid()).orElseGet(Summoner::new);

        RiotSummonerResponse summonerInfo = riotApiClient.getSummonerByPuuid(account.puuid());
        RiotLeagueEntryResponse soloQueue = riotApiClient.getLeagueEntriesByPuuid(account.puuid()).stream()
                .filter(entry -> SOLO_QUEUE.equals(entry.queueType()))
                .findFirst()
                .orElse(null);

        summoner.setPuuid(account.puuid());
        summoner.setGameName(account.gameName());
        summoner.setTagLine(account.tagLine());
        summoner.setProfileIconId(summonerInfo.profileIconId());
        summoner.setSummonerLevel(summonerInfo.summonerLevel());
        summoner.setTier(soloQueue != null ? soloQueue.tier() : null);
        summoner.setRank(soloQueue != null ? soloQueue.rank() : null);
        summoner.setLeaguePoints(soloQueue != null ? soloQueue.leaguePoints() : null);
        summoner.setWins(soloQueue != null ? soloQueue.wins() : null);
        summoner.setLosses(soloQueue != null ? soloQueue.losses() : null);
        summoner.setUpdatedAt(Instant.now());

        return summonerRepository.save(summoner);
    }

    private void recordSearch(Summoner summoner) {
        SearchCount searchCount = searchCountRepository.findById(summoner.getId())
                .orElseGet(() -> SearchCount.builder().summoner(summoner).build());
        searchCount.setSearchCount(searchCount.getSearchCount() + 1);
        searchCount.setLastSearchedAt(Instant.now());
        searchCountRepository.save(searchCount);
    }

    public List<Summoner> autocomplete(String query, int limit) {
        // Over-fetch before deduping: a (game_name, tag_line) can legitimately appear on
        // more than one row (old/new Riot ID owner - PROJECT_PLAN.md §6), so the raw query
        // can yield fewer distinct display names than `limit` rows.
        int fetchCap = Math.max(limit * 3, 20);
        List<Summoner> candidates = searchCountRepository.findSummonersByGameNamePrefix(
                query, PageRequest.of(0, fetchCap));

        // Already ordered by last_searched_at DESC, so keeping the first occurrence per
        // (game_name, tag_line) keeps whichever puuid was searched most recently.
        Map<String, Summoner> deduped = new LinkedHashMap<>();
        for (Summoner candidate : candidates) {
            deduped.putIfAbsent(candidate.getGameName() + "#" + candidate.getTagLine(), candidate);
        }
        return deduped.values().stream().limit(limit).toList();
    }

    public List<Summoner> popular(int limit) {
        return searchCountRepository.findPopularSummoners(PageRequest.of(0, limit));
    }
}
