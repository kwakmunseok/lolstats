package com.lolstats.client;

import com.lolstats.client.dto.RiotAccountResponse;
import com.lolstats.client.dto.RiotLeagueEntryResponse;
import com.lolstats.client.dto.RiotMatchResponse;
import com.lolstats.client.dto.RiotSummonerResponse;

import java.util.List;

// Interface exists so callers (SummonerService, match collection) can mock Riot calls with
// Mockito instead of hitting the real API in tests.
public interface RiotApiClient {

    RiotAccountResponse getAccountByRiotId(String gameName, String tagLine);

    RiotSummonerResponse getSummonerByPuuid(String puuid);

    List<RiotLeagueEntryResponse> getLeagueEntriesByPuuid(String puuid);

    List<String> getMatchIdsByPuuid(String puuid, int count);

    RiotMatchResponse getMatchById(String matchId);
}
