package com.lolstats.controller;

import com.lolstats.domain.Summoner;
import com.lolstats.dto.SummonerResponse;
import com.lolstats.dto.TierHistoryResponse;
import com.lolstats.repository.SummonerRepository;
import com.lolstats.repository.TierHistoryRepository;
import com.lolstats.service.MatchCollectionQueue;
import com.lolstats.service.MatchService;
import com.lolstats.service.SummonerService;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/summoners")
@Validated
public class SummonerController {

    private final SummonerService summonerService;
    private final MatchService matchService;
    private final MatchCollectionQueue matchCollectionQueue;
    private final SummonerRepository summonerRepository;
    private final TierHistoryRepository tierHistoryRepository;

    public SummonerController(
            SummonerService summonerService,
            MatchService matchService,
            MatchCollectionQueue matchCollectionQueue,
            SummonerRepository summonerRepository,
            TierHistoryRepository tierHistoryRepository) {
        this.summonerService = summonerService;
        this.matchService = matchService;
        this.matchCollectionQueue = matchCollectionQueue;
        this.summonerRepository = summonerRepository;
        this.tierHistoryRepository = tierHistoryRepository;
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
        triggerCollectionIfNeeded(summoner.getPuuid());
        return SummonerResponse.from(summoner);
    }

    // TTL 무관 강제 갱신 (PHASE2_PLAN.md Task 4). Cooldown check is the gate before doing any
    // work; the cooldown itself only gets set after the refresh + enqueue below succeed, so a
    // failed refresh (Riot error, etc.) doesn't cost the user the cooldown window.
    @PostMapping("/{summonerId}/refresh")
    public SummonerResponse refresh(@PathVariable Long summonerId) {
        if (summonerService.isRefreshCoolingDown(summonerId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해주세요");
        }

        Summoner summoner = summonerService.refresh(summonerId);
        triggerCollectionIfNeeded(summoner.getPuuid());
        summonerService.startRefreshCooldown(summonerId);

        return SummonerResponse.from(summoner);
    }

    private void triggerCollectionIfNeeded(String puuid) {
        MatchService.CollectionPlan plan = matchService.planCollection(puuid);
        if (!plan.missingMatchIds().isEmpty()) {
            matchCollectionQueue.enqueue(puuid, plan.totalCount());
        }
    }

    // 시계열 티어 이력 (PROJECT_PLAN.md §6 TIER_HISTORY) - 오래된 순, 차트 y축용 score 포함
    // (TierHistoryResponse.from - TierScore).
    @GetMapping("/{summonerId}/tier-history")
    public List<TierHistoryResponse> getTierHistory(@PathVariable Long summonerId) {
        if (!summonerRepository.existsById(summonerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "summoner not found: " + summonerId);
        }
        return tierHistoryRepository.findBySummonerIdOrderByRecordedAtAsc(summonerId).stream()
                .map(TierHistoryResponse::from)
                .toList();
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
