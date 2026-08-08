package com.lolstats.dto;

import com.lolstats.domain.SearchHistory;

import java.time.Instant;

public record SearchHistoryResponse(
        Long summonerId,
        String gameName,
        String tagLine,
        String tier,
        String rank,
        Integer leaguePoints,
        Instant searchedAt) {

    public static SearchHistoryResponse from(SearchHistory h) {
        var s = h.getSummoner();
        return new SearchHistoryResponse(
                s.getId(), s.getGameName(), s.getTagLine(), s.getTier(), s.getRank(), s.getLeaguePoints(),
                h.getSearchedAt());
    }
}
