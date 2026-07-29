package com.lolstats.service;

import com.lolstats.client.RiotApiClient;
import com.lolstats.client.dto.RiotMatchResponse;
import com.lolstats.domain.Match;
import com.lolstats.domain.MatchParticipant;
import com.lolstats.repository.MatchParticipantRepository;
import com.lolstats.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Service
public class MatchService {

    private static final int MATCH_ID_FETCH_COUNT = 20;
    // Plan allows 3~5 detail fetches per search (Phase 1 has no background queue yet);
    // 5 is the upper bound so a newly-searched summoner's history fills in fastest.
    private static final int DETAIL_FETCH_LIMIT = 5;

    // Summoner's Rift only (PROJECT_PLAN.md §4 MVP scope: 협곡 데이터만, ARAM/Arena 등 제외).
    // Normal Draft/Blind, Ranked Solo/Flex - Riot's numeric queueId, matching how queue_type
    // is stored (Task 4 decision: raw queueId, no name mapping until Phase 3).
    public static final List<String> RIFT_QUEUE_TYPES = List.of("400", "420", "430", "440");

    private final RiotApiClient riotApiClient;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final ObjectMapper objectMapper;

    public MatchService(
            RiotApiClient riotApiClient,
            MatchRepository matchRepository,
            MatchParticipantRepository matchParticipantRepository,
            ObjectMapper objectMapper) {
        this.riotApiClient = riotApiClient;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.objectMapper = objectMapper;
    }

    public void collectRecentMatches(String puuid) {
        List<String> matchIds = riotApiClient.getMatchIdsByPuuid(puuid, MATCH_ID_FETCH_COUNT);

        // Matches never change once played (PROJECT_PLAN.md principle ①) - anything
        // already in MATCHES is skipped rather than re-fetched.
        List<String> newMatchIds = matchIds.stream()
                .filter(id -> !matchRepository.existsByRiotMatchId(id))
                .limit(DETAIL_FETCH_LIMIT)
                .toList();

        for (String matchId : newMatchIds) {
            try {
                saveMatch(riotApiClient.getMatchById(matchId));
            } catch (HttpClientErrorException.TooManyRequests e) {
                // Phase 1 has no retry/backoff yet (that's Phase 2's Bucket4j) - keep
                // whatever was already saved and stop quietly instead of failing the search.
                break;
            }
        }
    }

    private void saveMatch(RiotMatchResponse response) {
        Match match = matchRepository.save(Match.builder()
                .riotMatchId(response.metadata().matchId())
                .gameCreation(Instant.ofEpochMilli(response.info().gameCreation()))
                .gameDuration(response.info().gameDuration())
                .queueType(String.valueOf(response.info().queueId()))
                .build());

        List<MatchParticipant> participants = response.info().participants().stream()
                .map(p -> toParticipant(match, p))
                .toList();
        matchParticipantRepository.saveAll(participants);
    }

    private MatchParticipant toParticipant(Match match, RiotMatchResponse.RiotMatchParticipant p) {
        return MatchParticipant.builder()
                .match(match)
                .puuid(p.puuid())
                .gameName(p.riotIdGameName())
                .tagLine(p.riotIdTagline())
                .championId(p.championId())
                .teamPosition(p.teamPosition())
                .kills(p.kills())
                .deaths(p.deaths())
                .assists(p.assists())
                .win(p.win())
                .spell1Id(p.summoner1Id())
                .spell2Id(p.summoner2Id())
                .itemsJson(writeJson(List.of(
                        p.item0(), p.item1(), p.item2(), p.item3(), p.item4(), p.item5(), p.item6())))
                .runesJson(writeJson(p.perks()))
                .build();
    }

    private String writeJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
