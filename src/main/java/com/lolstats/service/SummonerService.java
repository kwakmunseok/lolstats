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
import org.springframework.stereotype.Service;

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

    private final SummonerRepository summonerRepository;
    private final SearchCountRepository searchCountRepository;
    private final RiotApiClient riotApiClient;
    private final long ttlMinutes;

    public SummonerService(
            SummonerRepository summonerRepository,
            SearchCountRepository searchCountRepository,
            RiotApiClient riotApiClient,
            @Value("${app.cache.summoner-ttl-minutes}") long ttlMinutes) {
        this.summonerRepository = summonerRepository;
        this.searchCountRepository = searchCountRepository;
        this.riotApiClient = riotApiClient;
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
