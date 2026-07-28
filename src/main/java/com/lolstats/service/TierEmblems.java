package com.lolstats.service;

// Static resources at src/main/resources/static/images/tier-emblems/{TIER}.png, downloaded
// from Riot's developer portal (ddragon has no tier emblems - PROJECT_PLAN.md §4 Phase 1).
// Filenames match Riot's tier strings (IRON, BRONZE, ..., CHALLENGER) exactly, so no lookup
// table is needed - just resolving the convention in one place instead of in template markup.
public final class TierEmblems {

    private TierEmblems() {
    }

    public static String imageUrl(String tier) {
        return tier == null ? null : "/images/tier-emblems/" + tier + ".png";
    }
}
