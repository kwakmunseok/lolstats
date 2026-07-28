package com.lolstats.client.dto;

// summoner-v4 by-puuid response (platform routing)
public record RiotSummonerResponse(String puuid, Integer profileIconId, Integer summonerLevel) {
}
