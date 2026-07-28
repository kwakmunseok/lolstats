package com.lolstats.client.ddragon.dto;

import java.util.Map;

// summoner.json: data is keyed by internal spell name (e.g. "SummonerFlash") - each entry's
// own "key" field holds the numeric spell id (matches MATCH_PARTICIPANTS.spell1_id/spell2_id).
public record SummonerSpellListResponse(Map<String, SpellData> data) {

    public record SpellData(String key, String name, SpellImage image) {
    }

    public record SpellImage(String full) {
    }
}
