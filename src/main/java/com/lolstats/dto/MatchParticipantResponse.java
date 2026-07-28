package com.lolstats.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.lolstats.domain.MatchParticipant;

// items/runes are stored as pre-formed JSON text (MATCH_PARTICIPANTS.items_json/runes_json);
// @JsonRawValue embeds them as nested JSON instead of a double-encoded string.
public record MatchParticipantResponse(
        String puuid,
        String gameName,
        String tagLine,
        Integer championId,
        String teamPosition,
        Integer kills,
        Integer deaths,
        Integer assists,
        Boolean win,
        Integer spell1Id,
        Integer spell2Id,
        @JsonRawValue String items,
        @JsonRawValue String runes) {

    public static MatchParticipantResponse from(MatchParticipant p) {
        return new MatchParticipantResponse(
                p.getPuuid(), p.getGameName(), p.getTagLine(), p.getChampionId(), p.getTeamPosition(),
                p.getKills(), p.getDeaths(), p.getAssists(), p.getWin(), p.getSpell1Id(), p.getSpell2Id(),
                p.getItemsJson(), p.getRunesJson());
    }
}
