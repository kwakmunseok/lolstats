package com.lolstats.dto;

import com.lolstats.domain.Favorite;

import java.time.Instant;

public record FavoriteResponse(
        Long summonerId,
        String gameName,
        String tagLine,
        String tier,
        String rank,
        Integer leaguePoints,
        Instant favoritedAt) {

    public static FavoriteResponse from(Favorite f) {
        var s = f.getSummoner();
        return new FavoriteResponse(
                s.getId(), s.getGameName(), s.getTagLine(), s.getTier(), s.getRank(), s.getLeaguePoints(),
                f.getCreatedAt());
    }
}
