package com.lolstats.dto;

import com.lolstats.domain.Summoner;

import java.time.Instant;

// Matches PROJECT_PLAN.md §7 response example exactly.
public record SummonerResponse(
        Long id,
        String gameName,
        String tagLine,
        Integer profileIconId,
        Integer summonerLevel,
        String tier,
        String rank,
        Integer leaguePoints,
        Integer wins,
        Integer losses,
        Instant updatedAt) {

    public static SummonerResponse from(Summoner s) {
        return new SummonerResponse(
                s.getId(), s.getGameName(), s.getTagLine(), s.getProfileIconId(), s.getSummonerLevel(),
                s.getTier(), s.getRank(), s.getLeaguePoints(), s.getWins(), s.getLosses(), s.getUpdatedAt());
    }
}
