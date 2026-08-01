package com.lolstats.client.dto;

// One entry inside a RiotLeagueListResponse (apex leagues) - no tier/queueType field, both
// are implied by the wrapper.
public record RiotLeagueItemResponse(
        String puuid,
        String rank,
        Integer leaguePoints,
        Integer wins,
        Integer losses) {
}
