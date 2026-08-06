package com.lolstats.controller;

import com.lolstats.domain.Match;
import com.lolstats.domain.MatchParticipant;
import com.lolstats.domain.Summoner;
import com.lolstats.dto.ChampionStatsResponse;
import com.lolstats.dto.MatchDetailResponse;
import com.lolstats.dto.MatchListResponse;
import com.lolstats.dto.MatchSummaryResponse;
import com.lolstats.repository.MatchParticipantRepository;
import com.lolstats.repository.MatchRepository;
import com.lolstats.repository.SummonerRepository;
import com.lolstats.service.ChampionStatsService;
import com.lolstats.service.MatchCollectionQueue;
import com.lolstats.service.MatchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MatchController {

    private final SummonerRepository summonerRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final MatchCollectionQueue matchCollectionQueue;
    private final ChampionStatsService championStatsService;

    public MatchController(
            SummonerRepository summonerRepository,
            MatchRepository matchRepository,
            MatchParticipantRepository matchParticipantRepository,
            MatchCollectionQueue matchCollectionQueue,
            ChampionStatsService championStatsService) {
        this.summonerRepository = summonerRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.matchCollectionQueue = matchCollectionQueue;
        this.championStatsService = championStatsService;
    }

    // Reads whatever is already in the DB; collection itself happens in the background
    // (MatchCollectionQueue, triggered from SummonerController/PageController on search -
    // PHASE2_PLAN.md Task 3). collecting/collectedCount/totalCount let FE poll progress.
    @GetMapping("/summoners/{summonerId}/matches")
    public MatchListResponse getMatches(
            @PathVariable Long summonerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Summoner summoner = summonerRepository.findById(summonerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "summoner not found: " + summonerId));

        Pageable pageable = PageRequest.of(page, size, Sort.by("match.gameCreation").descending());
        Page<MatchSummaryResponse> matches = matchParticipantRepository.findByPuuidAndMatch_QueueTypeIn(
                        summoner.getPuuid(), MatchService.RIFT_QUEUE_TYPES, pageable)
                .map(MatchSummaryResponse::from);

        boolean collecting = matchCollectionQueue.isCollecting(summoner.getPuuid());
        Integer totalCount = matchCollectionQueue.totalCount(summoner.getPuuid());
        // Counted across all queue types (matching what totalCount counts, per Riot's raw id
        // list), not the Rift-only `matches` page above - a mixed-queue puuid would otherwise
        // never show 100% since non-Rift matches never appear in the displayed list.
        Integer collectedCount = collecting && totalCount != null
                ? Math.min(matchParticipantRepository.findByPuuid(summoner.getPuuid()).size(), totalCount)
                : null;

        return MatchListResponse.of(matches, collecting, collectedCount, totalCount);
    }

    // 승률/최근 폼/챔피언별 통계 - 랭크/드래프트 큐만 집계 (PROJECT_PLAN.md §4 Phase 3 큐 필터,
    // ChampionStatsService가 MatchService.RIFT_QUEUE_TYPES 재사용).
    @GetMapping("/summoners/{summonerId}/champion-stats")
    public ChampionStatsResponse getChampionStats(@PathVariable Long summonerId) {
        Summoner summoner = summonerRepository.findById(summonerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "summoner not found: " + summonerId));
        return championStatsService.stats(summoner.getPuuid());
    }

    @GetMapping("/matches/{riotMatchId}")
    public MatchDetailResponse getMatch(@PathVariable String riotMatchId) {
        Match match = matchRepository.findByRiotMatchId(riotMatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "match not found: " + riotMatchId));

        List<MatchParticipant> participants = matchParticipantRepository.findByMatchId(match.getId());
        return MatchDetailResponse.from(match, participants);
    }
}
