package com.lolstats.dto;

import java.util.List;

// games/overallWinRate/recentForm/perChampion bundled into one response - PHASE3_PLAN.md §5
// open question resolved this way for now (single route documented, easy to split later since
// ChampionStatsService computes the three aggregates independently).
public record ChampionStatsResponse(
        int games,
        double overallWinRate,
        List<Boolean> recentForm,
        List<ChampionStatRow> perChampion) {
}
