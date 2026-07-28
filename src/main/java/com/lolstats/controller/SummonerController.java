package com.lolstats.controller;

import com.lolstats.domain.Summoner;
import com.lolstats.dto.SummonerResponse;
import com.lolstats.service.MatchService;
import com.lolstats.service.SummonerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/summoners")
public class SummonerController {

    private final SummonerService summonerService;
    private final MatchService matchService;

    public SummonerController(SummonerService summonerService, MatchService matchService) {
        this.summonerService = summonerService;
        this.matchService = matchService;
    }

    // "riot-id" prefix keeps this 2-segment route from colliding with /{summonerId}/matches
    // (PROJECT_PLAN.md §7 경로 설계 노트).
    @GetMapping("/riot-id/{gameName}/{tagLine}")
    public SummonerResponse getByRiotId(@PathVariable String gameName, @PathVariable String tagLine) {
        Summoner summoner = summonerService.findOrFetch(gameName, tagLine);
        // Sync, best-effort collection (Phase 1: no background queue yet - that's Phase 2).
        matchService.collectRecentMatches(summoner.getPuuid());
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
