package com.lolstats.dto;

import com.lolstats.domain.Match;
import com.lolstats.domain.MatchParticipant;

import java.time.Instant;

// One row per match, from the searched player's own participant record (profile match list).
public record MatchSummaryResponse(
        String riotMatchId,
        Instant gameCreation,
        Integer gameDuration,
        String queueType,
        MatchParticipantResponse participant) {

    public static MatchSummaryResponse from(MatchParticipant p) {
        Match match = p.getMatch();
        return new MatchSummaryResponse(
                match.getRiotMatchId(), match.getGameCreation(), match.getGameDuration(), match.getQueueType(),
                MatchParticipantResponse.from(p));
    }
}
