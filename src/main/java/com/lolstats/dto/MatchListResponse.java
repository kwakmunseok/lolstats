package com.lolstats.dto;

import org.springframework.data.domain.Page;

import java.util.List;

// collecting/collectedCount/totalCount are the background-collection status fields FE polls
// while the queue (Phase 2 Task 3) fills in match history - null when nothing is collecting.
public record MatchListResponse(
        List<MatchSummaryResponse> matches,
        long totalElements,
        boolean collecting,
        Integer collectedCount,
        Integer totalCount) {

    public static MatchListResponse of(
            Page<MatchSummaryResponse> page, boolean collecting, Integer collectedCount, Integer totalCount) {
        return new MatchListResponse(page.getContent(), page.getTotalElements(), collecting, collectedCount, totalCount);
    }
}
