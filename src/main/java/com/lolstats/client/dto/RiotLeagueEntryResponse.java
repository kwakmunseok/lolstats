package com.lolstats.client.dto;

// league-v4 by-puuid response (platform routing); one entry per queue, filter by queueType
public record RiotLeagueEntryResponse(
        String queueType,
        String tier,
        String rank,
        Integer leaguePoints,
        Integer wins,
        Integer losses) {
}
