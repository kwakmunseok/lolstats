package com.lolstats.dto;

import com.lolstats.domain.Match;
import com.lolstats.domain.MatchParticipant;

import java.time.Instant;
import java.util.List;

// All 10 participants, for the match detail screen.
public record MatchDetailResponse(
        String riotMatchId,
        Instant gameCreation,
        Integer gameDuration,
        String queueType,
        List<MatchParticipantResponse> participants) {

    public static MatchDetailResponse from(Match match, List<MatchParticipant> participants) {
        return new MatchDetailResponse(
                match.getRiotMatchId(), match.getGameCreation(), match.getGameDuration(), match.getQueueType(),
                participants.stream().map(MatchParticipantResponse::from).toList());
    }
}
