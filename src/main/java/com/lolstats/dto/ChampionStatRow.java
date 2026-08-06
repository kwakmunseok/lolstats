package com.lolstats.dto;

public record ChampionStatRow(
        Integer championId,
        int games,
        int wins,
        double winRate,
        double avgKda) {
}
