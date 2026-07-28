package com.lolstats.client.ddragon.dto;

import java.util.Map;

// champion.json: data is keyed by internal champion name (e.g. "Ahri"), not championId -
// each entry's own "key" field holds the numeric championId as a string.
public record ChampionListResponse(Map<String, ChampionData> data) {

    public record ChampionData(String key, String name, ChampionImage image) {
    }

    public record ChampionImage(String full) {
    }
}
