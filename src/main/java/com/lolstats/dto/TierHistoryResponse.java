package com.lolstats.dto;

import com.lolstats.domain.TierHistory;
import com.lolstats.service.TierScore;

import java.time.Instant;

// tier/rank/leaguePoints are for the tooltip/label; score is the chart y-axis value
// (TierScore - tier/rank are categorical, can't plot directly).
public record TierHistoryResponse(
        Instant recordedAt,
        String tier,
        String rank,
        Integer leaguePoints,
        int score) {

    public static TierHistoryResponse from(TierHistory h) {
        return new TierHistoryResponse(
                h.getRecordedAt(), h.getTier(), h.getRank(), h.getLeaguePoints(),
                TierScore.score(h.getTier(), h.getRank(), h.getLeaguePoints()));
    }
}
