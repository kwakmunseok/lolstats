package com.lolstats.service;

import java.util.Map;

// Display-only label for Match.queueType (stored as Riot's raw numeric queueId string -
// MatchService.RIFT_QUEUE_TYPES comment: "no name mapping until Phase 3"). DB keeps the raw
// id (principle ① - never transform stored data); this only covers the 4 ids actually shown
// on screen (MatchService.RIFT_QUEUE_TYPES) - no need to parse Data Dragon's full queues.json.
public final class QueueNames {

    private static final Map<String, String> NAMES = Map.of(
            "420", "솔로랭크",
            "440", "자유랭크",
            "400", "일반(드래프트)",
            "430", "일반(블라인드)");

    private QueueNames() {
    }

    public static String displayName(String queueTypeId) {
        return NAMES.getOrDefault(queueTypeId, queueTypeId);
    }
}
