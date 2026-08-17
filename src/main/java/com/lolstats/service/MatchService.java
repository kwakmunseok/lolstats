package com.lolstats.service;

import com.lolstats.client.RiotApiClient;
import com.lolstats.client.dto.RiotMatchResponse;
import com.lolstats.client.dto.RiotMatchTimelineResponse;
import com.lolstats.domain.Match;
import com.lolstats.domain.MatchParticipant;
import com.lolstats.dto.ItemEvent;
import com.lolstats.repository.MatchParticipantRepository;
import com.lolstats.repository.MatchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class MatchService {

    private static final int MATCH_ID_FETCH_COUNT = 20;

    // Summoner's Rift only (PROJECT_PLAN.md §4 MVP scope: 협곡 데이터만, ARAM/Arena 등 제외).
    // Normal Draft/Blind, Ranked Solo/Flex - Riot's numeric queueId, matching how queue_type
    // is stored (Task 4 decision: raw queueId, no name mapping until Phase 3).
    public static final List<String> RIFT_QUEUE_TYPES = List.of("400", "420", "430", "440");

    private final RiotApiClient riotApiClient;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public MatchService(
            RiotApiClient riotApiClient,
            MatchRepository matchRepository,
            MatchParticipantRepository matchParticipantRepository,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.riotApiClient = riotApiClient;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.objectMapper = objectMapper;
        // TransactionTemplate instead of @Transactional: saveMatch() is called from within
        // this same bean (collectMatches() loop), and Spring's proxy-based @Transactional
        // does nothing on that kind of self-invocation - this way a failure between the match
        // insert and its participants insert actually rolls both back instead of leaving an
        // orphaned Match row (found live during Task 3 verification - PHASE2_PLAN.md).
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // totalCount is every id Riot returned (any queue); missingMatchIds is whatever isn't
    // already cached (PROJECT_PLAN.md principle ① - never re-request an already-seen match).
    public record CollectionPlan(int totalCount, List<String> missingMatchIds) {
    }

    // Cheap (1 Riot call) - called synchronously from the search request so the response can
    // report totalCount immediately and decide whether there's anything to hand off to the
    // background queue (Phase 2 Task 3). The actual fetching happens in collectMatches().
    public CollectionPlan planCollection(String puuid) {
        List<String> matchIds = riotApiClient.getMatchIdsByPuuid(puuid, MATCH_ID_FETCH_COUNT);
        List<String> missing = matchIds.stream()
                .filter(id -> !matchRepository.existsByRiotMatchId(id))
                .toList();
        return new CollectionPlan(matchIds.size(), missing);
    }

    // complete=false means a 429 cut the run short (savedCount is whatever got through before
    // that). The crawler (CRAWLER_PLAN.md §3 Task 2) needs this to tell "fully backfilled" from
    // "partially backfilled" - the latter must stay eligible for retry, not get marked done.
    public record CollectionResult(int savedCount, boolean complete) {
    }

    // Phase 1 capped this at 5 detail fetches per search; Phase 2's background worker isn't
    // blocking a request thread anymore, so it works through everything missing.
    public CollectionResult collectMatches(List<String> matchIds, Runnable afterEachSave) {
        int savedCount = 0;
        for (String matchId : matchIds) {
            try {
                RiotMatchResponse response = riotApiClient.getMatchById(matchId);
                saveMatch(response, fetchItemEventsJson(matchId));
                savedCount++;
                afterEachSave.run();
            } catch (HttpClientErrorException.TooManyRequests e) {
                // RiotApiClientImpl already retries 429s a bounded number of times (Phase 2
                // Task 2) - this only triggers once those retries are exhausted, so stop
                // quietly and keep whatever was already saved rather than fail the whole run.
                return new CollectionResult(savedCount, false);
            }
        }
        return new CollectionResult(savedCount, true);
    }

    private void saveMatch(RiotMatchResponse response, String itemEventsJson) {
        transactionTemplate.executeWithoutResult(status -> {
            Match match = matchRepository.save(Match.builder()
                    .riotMatchId(response.metadata().matchId())
                    .gameCreation(Instant.ofEpochMilli(response.info().gameCreation()))
                    .gameDuration(response.info().gameDuration())
                    .queueType(String.valueOf(response.info().queueId()))
                    .itemEventsJson(itemEventsJson)
                    .build());

            List<MatchParticipant> participants = response.info().participants().stream()
                    .map(p -> toParticipant(match, p))
                    .toList();
            matchParticipantRepository.saveAll(participants);
        });
    }

    // Timeline은 매치 상세와 별개의 Riot API 호출 - 여기서 실패(429/5xx/타임아웃)해도 매치 저장
    // 자체를 막으면 안 되므로 null을 돌려주고 계속 진행한다(PageController는 null이면 빌드 오더
    // 줄을 그냥 생략함, 에러 아님).
    private String fetchItemEventsJson(String matchId) {
        RiotMatchTimelineResponse timeline;
        try {
            timeline = riotApiClient.getMatchTimeline(matchId);
        } catch (RuntimeException e) {
            log.warn("Failed to fetch match timeline for {} - item build order will be unavailable", matchId, e);
            return null;
        }
        // Tests that don't stub getMatchTimeline() get null back from Mockito - same "no
        // timeline data" outcome as a caught failure above, not a separate case to test.
        if (timeline == null) {
            return null;
        }
        List<ItemEvent> events = timeline.info().frames().stream()
                .flatMap(f -> f.events().stream())
                .filter(e -> "ITEM_PURCHASED".equals(e.path("type").asString(""))
                        || "ITEM_SOLD".equals(e.path("type").asString("")))
                .map(e -> new ItemEvent(
                        e.path("participantId").asInt(0),
                        e.path("itemId").asInt(0),
                        e.path("type").asString(""),
                        e.path("timestamp").asLong(0)))
                .toList();
        return writeJson(events);
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
