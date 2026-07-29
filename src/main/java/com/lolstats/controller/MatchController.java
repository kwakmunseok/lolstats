package com.lolstats.controller;

import com.lolstats.domain.Match;
import com.lolstats.domain.MatchParticipant;
import com.lolstats.domain.Summoner;
import com.lolstats.dto.MatchDetailResponse;
import com.lolstats.dto.MatchSummaryResponse;
import com.lolstats.repository.MatchParticipantRepository;
import com.lolstats.repository.MatchRepository;
import com.lolstats.repository.SummonerRepository;
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

    public MatchController(
            SummonerRepository summonerRepository,
            MatchRepository matchRepository,
            MatchParticipantRepository matchParticipantRepository) {
        this.summonerRepository = summonerRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
    }

    // Phase 1: reads whatever is already in the DB, no live collection triggered here
    // (that happens once, from SummonerController on search - see its DoD in PHASE1_PLAN.md
    // Task 4). No `collecting` status field yet - that's Phase 2's background queue.
    @GetMapping("/summoners/{summonerId}/matches")
    public Page<MatchSummaryResponse> getMatches(
            @PathVariable Long summonerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Summoner summoner = summonerRepository.findById(summonerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "summoner not found: " + summonerId));

        Pageable pageable = PageRequest.of(page, size, Sort.by("match.gameCreation").descending());
        return matchParticipantRepository.findByPuuidAndMatch_QueueTypeIn(
                        summoner.getPuuid(), MatchService.RIFT_QUEUE_TYPES, pageable)
                .map(MatchSummaryResponse::from);
    }

    @GetMapping("/matches/{riotMatchId}")
    public MatchDetailResponse getMatch(@PathVariable String riotMatchId) {
        Match match = matchRepository.findByRiotMatchId(riotMatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "match not found: " + riotMatchId));

        List<MatchParticipant> participants = matchParticipantRepository.findByMatchId(match.getId());
        return MatchDetailResponse.from(match, participants);
    }
}
