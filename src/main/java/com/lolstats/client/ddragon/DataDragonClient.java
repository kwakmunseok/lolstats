package com.lolstats.client.ddragon;

import com.lolstats.client.ddragon.dto.ChampionListResponse;
import com.lolstats.client.ddragon.dto.ItemListResponse;
import com.lolstats.client.ddragon.dto.RuneTreeResponse;
import com.lolstats.client.ddragon.dto.SummonerSpellListResponse;

import java.util.List;

// Interface exists so DataDragonService can be unit-tested with Mockito, same reasoning
// as RiotApiClient. No API key, no rate limit - Data Dragon is a public static CDN.
public interface DataDragonClient {

    List<String> getVersions();

    ChampionListResponse getChampions(String version);

    ItemListResponse getItems(String version);

    SummonerSpellListResponse getSummonerSpells(String version);

    List<RuneTreeResponse> getRuneTrees(String version);
}
