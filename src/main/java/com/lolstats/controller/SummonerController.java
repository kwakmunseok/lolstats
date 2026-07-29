package com.lolstats.controller;

import com.lolstats.domain.Summoner;
import com.lolstats.dto.SummonerResponse;
import com.lolstats.service.MatchCollectionQueue;
import com.lolstats.service.MatchService;
import com.lolstats.service.SummonerService;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/summoners")
@Validated
public class SummonerController {

    private final SummonerService summonerService;
    private final MatchService matchService;
    private final MatchCollectionQueue matchCollectionQueue;

    public SummonerController(
            SummonerService summonerService, MatchService matchService, MatchCollectionQueue matchCollectionQueue) {
        this.summonerService = summonerService;
        this.matchService = matchService;
        this.matchCollectionQueue = matchCollectionQueue;
    }

    // "riot-id" prefix keeps this 2-segment route from colliding with /{summonerId}/matches
    // (PROJECT_PLAN.md §7 경로 설계 노트). Length bounds are Riot's own Riot ID policy
    // (PHASE1_PLAN.md Task 1) - BE is the final gate, FE validation below is UX-only.
    @GetMapping("/riot-id/{gameName}/{tagLine}")
    public SummonerResponse getByRiotId(
            @PathVariable @Size(min = 3, max = 16, message = "게임 이름은 3~16자여야 합니다") String gameName,
            @PathVariable @Size(min = 3, max = 5, message = "태그라인은 3~5자여야 합니다") String tagLine) {
        Summoner summoner = summonerService.findOrFetch(gameName, tagLine);
        // Response returns immediately with whatever's cached; missing matches are handed to
        // the background queue instead of fetched synchronously (Phase 2 Task 3).
        MatchService.CollectionPlan plan = matchService.planCollection(summoner.getPuuid());
        if (!plan.missingMatchIds().isEmpty()) {
            matchCollectionQueue.enqueue(summoner.getPuuid(), plan.totalCount());
        }
        return SummonerResponse.from(summoner);
    }

    @GetMapping("/autocomplete")
    public List<SummonerResponse> autocomplete(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        return summonerService.autocomplete(query, limit).stream()
                .map(SummonerResponse::from)
                .toList();
    }

    // Phase 1: DB-only (SEARCH_COUNTS is the source of truth either way). Redis ZSET
    // read-through comes in Phase 2 - PROJECT_PLAN.md §4 원칙, §7.
    @GetMapping("/popular")
    public List<SummonerResponse> popular(@RequestParam(defaultValue = "10") int limit) {
        return summonerService.popular(limit).stream()
                .map(SummonerResponse::from)
                .toList();
    }
}
