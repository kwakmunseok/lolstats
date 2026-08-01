package com.lolstats.client.dto;

// Unified shape the crawler works with, regardless of which league-v4 listing endpoint
// produced it. Field names match league-v4 entries/{queue}/{tier}/{division} exactly, so
// Jackson deserializes that endpoint's response directly into this record; the apex
// endpoints (challengerleagues/grandmasterleagues/masterleagues) return a different raw
// shape (RiotLeagueListResponse) that RiotApiClientImpl maps into this one.
public record RiotLeagueSeedEntryResponse(
        String puuid,
        String tier,
        String rank,
        Integer leaguePoints,
        Integer wins,
        Integer losses) {
}
