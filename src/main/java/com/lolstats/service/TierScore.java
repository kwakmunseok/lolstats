package com.lolstats.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

// Sortable single-number stand-in for tier/rank/LP (categorical, can't go on a chart y-axis
// directly - PHASE3_PLAN.md §5). ponytail: fixed 400-point-per-tier scale, division worth a
// flat 100 within a tier - real population distribution per tier isn't even, but this only
// needs to show "trending up or down", not be an authoritative MMR estimate. Revisit if a more
// precise ranking is ever needed.
public final class TierScore {

    private static final List<String> TIER_ORDER = List.of(
            "IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND",
            "MASTER", "GRANDMASTER", "CHALLENGER");

    // Master+ has no division - league-v4 still returns rank="I" for these (not null), so the
    // apex check must key off the tier name, not an absent rank.
    private static final Set<String> APEX_TIERS = Set.of("MASTER", "GRANDMASTER", "CHALLENGER");

    private static final Map<String, Integer> DIVISION_OFFSET = Map.of("I", 300, "II", 200, "III", 100, "IV", 0);

    private TierScore() {
    }

    // tier is assumed non-null - callers only ever hold TIER_HISTORY rows, and unranked
    // (tier == null) is never written there (SummonerService.recordTierHistory).
    public static int score(String tier, String rank, int leaguePoints) {
        int tierIndex = TIER_ORDER.indexOf(tier);
        int divisionOffset = APEX_TIERS.contains(tier) ? 0 : DIVISION_OFFSET.getOrDefault(rank, 0);
        return tierIndex * 400 + divisionOffset + leaguePoints;
    }
}
