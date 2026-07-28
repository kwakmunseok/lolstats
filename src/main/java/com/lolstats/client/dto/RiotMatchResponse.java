package com.lolstats.client.dto;

// match-v5 match detail response (regional routing)
public record RiotMatchResponse(RiotMatchMetadata metadata, RiotMatchInfo info) {

    public record RiotMatchMetadata(String matchId) {
    }

    public record RiotMatchInfo(
            long gameCreation,
            int gameDuration,
            int queueId,
            java.util.List<RiotMatchParticipant> participants) {
    }

    // perks kept as a raw JsonNode: Task 4 decides what subset of the rune tree to persist
    public record RiotMatchParticipant(
            String puuid,
            String riotIdGameName,
            String riotIdTagline,
            int championId,
            String teamPosition,
            int kills,
            int deaths,
            int assists,
            boolean win,
            int summoner1Id,
            int summoner2Id,
            int item0,
            int item1,
            int item2,
            int item3,
            int item4,
            int item5,
            int item6,
            tools.jackson.databind.JsonNode perks) {
    }
}
