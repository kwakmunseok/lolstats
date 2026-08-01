package com.lolstats.client;

import com.lolstats.client.dto.RiotAccountResponse;
import com.lolstats.client.dto.RiotLeagueEntryResponse;
import com.lolstats.client.dto.RiotLeagueSeedEntryResponse;
import com.lolstats.client.dto.RiotMatchResponse;
import com.lolstats.client.dto.RiotSummonerResponse;

import java.util.List;

// Interface exists so callers (SummonerService, match collection) can mock Riot calls with
// Mockito instead of hitting the real API in tests.
public interface RiotApiClient {

    RiotAccountResponse getAccountByRiotId(String gameName, String tagLine);

    // by-puuid variant (regional routing, same account-v1 endpoint) - the crawler only ever
    // has a puuid to start from (league-v4 listings, not a searched Riot ID).
    RiotAccountResponse getAccountByPuuid(String puuid);

    RiotSummonerResponse getSummonerByPuuid(String puuid);

    List<RiotLeagueEntryResponse> getLeagueEntriesByPuuid(String puuid);

    // league-v4 apex tiers (CRAWLER_PLAN.md §0) - one call each, full tier returned, no paging.
    List<RiotLeagueSeedEntryResponse> getChallengerLeague(String queue);

    List<RiotLeagueSeedEntryResponse> getGrandmasterLeague(String queue);

    List<RiotLeagueSeedEntryResponse> getMasterLeague(String queue);

    // league-v4 entries/{queue}/{tier}/{division}?page= (diamond down to iron) - empty list
    // is the pagination end condition.
    List<RiotLeagueSeedEntryResponse> getLeagueEntries(String queue, String tier, String division, int page);

    List<String> getMatchIdsByPuuid(String puuid, int count);

    RiotMatchResponse getMatchById(String matchId);
}
