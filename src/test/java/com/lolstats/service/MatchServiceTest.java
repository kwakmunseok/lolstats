package com.lolstats.service;

import com.lolstats.client.RiotApiClient;
import com.lolstats.client.dto.RiotMatchResponse;
import com.lolstats.client.dto.RiotMatchTimelineResponse;
import com.lolstats.domain.Match;
import com.lolstats.domain.MatchParticipant;
import com.lolstats.repository.MatchParticipantRepository;
import com.lolstats.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private RiotApiClient riotApiClient;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchParticipantRepository matchParticipantRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    private MatchService service;

    @BeforeEach
    void setUp() {
        service = new MatchService(
                riotApiClient, matchRepository, matchParticipantRepository, JsonMapper.builder().build(), transactionManager);
        lenient().when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // TransactionTemplate delegates to the manager for begin/commit - a mocked manager
        // just needs to hand back a status object so the callback still runs synchronously.
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    private static RiotMatchResponse sampleMatch(String matchId) {
        return new RiotMatchResponse(
                new RiotMatchResponse.RiotMatchMetadata(matchId),
                new RiotMatchResponse.RiotMatchInfo(1700000000000L, 1800, 420, List.of(
                        new RiotMatchResponse.RiotMatchParticipant(
                                "puuid-1", "Faker", "KR1", 103, "MIDDLE",
                                10, 2, 8, true, 4, 12,
                                1001, 0, 0, 0, 0, 0, 3340,
                                JsonMapper.builder().build().readTree("{}")))));
    }

    private static RiotMatchTimelineResponse sampleTimeline() {
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode purchased = mapper.readTree("""
                {"type":"ITEM_PURCHASED","timestamp":65000,"participantId":1,"itemId":1055}
                """);
        JsonNode sold = mapper.readTree("""
                {"type":"ITEM_SOLD","timestamp":120000,"participantId":1,"itemId":1055}
                """);
        JsonNode kill = mapper.readTree("""
                {"type":"CHAMPION_KILL","timestamp":90000,"killerId":1,"victimId":6}
                """);
        return new RiotMatchTimelineResponse(new RiotMatchTimelineResponse.RiotMatchTimelineInfo(
                List.of(new RiotMatchTimelineResponse.RiotMatchTimelineFrame(List.of(purchased, sold, kill)))));
    }

    @Test
    void planCollection_excludesMatchIdsAlreadyInDbButKeepsTotalCount() {
        when(riotApiClient.getMatchIdsByPuuid("puuid-1", 20))
                .thenReturn(List.of("KR_1", "KR_2", "KR_3"));
        when(matchRepository.existsByRiotMatchId("KR_1")).thenReturn(true);
        when(matchRepository.existsByRiotMatchId("KR_2")).thenReturn(false);
        when(matchRepository.existsByRiotMatchId("KR_3")).thenReturn(false);

        MatchService.CollectionPlan plan = service.planCollection("puuid-1");

        assertEquals(3, plan.totalCount());
        assertEquals(List.of("KR_2", "KR_3"), plan.missingMatchIds());
    }

    @Test
    void collectMatches_fetchesEveryProvidedId_noCap() {
        // Phase 1 capped this at 5; Phase 2's background worker isn't blocking a request
        // thread anymore, so all provided ids get processed.
        List<String> ids = List.of("KR_1", "KR_2", "KR_3", "KR_4", "KR_5", "KR_6", "KR_7");
        for (String id : ids) {
            when(riotApiClient.getMatchById(id)).thenReturn(sampleMatch(id));
        }

        MatchService.CollectionResult result = service.collectMatches(ids, () -> {
        });

        verify(riotApiClient, times(7)).getMatchById(any());
        assertEquals(7, result.savedCount());
        assertTrue(result.complete());
    }

    @Test
    void collectMatches_invokesCallbackOnceForEachSave() {
        List<String> ids = List.of("KR_1", "KR_2");
        when(riotApiClient.getMatchById("KR_1")).thenReturn(sampleMatch("KR_1"));
        when(riotApiClient.getMatchById("KR_2")).thenReturn(sampleMatch("KR_2"));
        AtomicInteger callbackCount = new AtomicInteger();

        service.collectMatches(ids, callbackCount::incrementAndGet);

        assertEquals(2, callbackCount.get());
    }

    @Test
    void collectMatches_savesMatchAndParticipantsWithMappedFields() {
        when(riotApiClient.getMatchById("KR_1")).thenReturn(sampleMatch("KR_1"));

        service.collectMatches(List.of("KR_1"), () -> {
        });

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertEquals("KR_1", matchCaptor.getValue().getRiotMatchId());
        assertEquals(1800, matchCaptor.getValue().getGameDuration());
        assertEquals("420", matchCaptor.getValue().getQueueType());

        ArgumentCaptor<List<MatchParticipant>> participantsCaptor = ArgumentCaptor.forClass(List.class);
        verify(matchParticipantRepository).saveAll(participantsCaptor.capture());
        MatchParticipant saved = participantsCaptor.getValue().get(0);
        assertEquals("Faker", saved.getGameName());
        assertEquals(103, saved.getChampionId());
        assertEquals(4, saved.getSpell1Id());
        assertEquals(12, saved.getSpell2Id());
        assertTrue(saved.getItemsJson().contains("1001"));
    }

    @Test
    void collectMatches_stopsQuietlyOn429_keepsAlreadySavedMatches() {
        when(riotApiClient.getMatchById("KR_1")).thenReturn(sampleMatch("KR_1"));
        when(riotApiClient.getMatchById("KR_2"))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null));

        MatchService.CollectionResult result = service.collectMatches(List.of("KR_1", "KR_2", "KR_3"), () -> {
        });

        verify(riotApiClient).getMatchById("KR_1");
        verify(riotApiClient).getMatchById("KR_2");
        verify(riotApiClient, never()).getMatchById("KR_3");
        verify(matchRepository, times(1)).save(any(Match.class));
        assertEquals(1, result.savedCount());
        assertFalse(result.complete());
    }

    @Test
    void collectMatches_savesItemEventsJson_purchasedAndSoldOnly() {
        when(riotApiClient.getMatchById("KR_1")).thenReturn(sampleMatch("KR_1"));
        when(riotApiClient.getMatchTimeline("KR_1")).thenReturn(sampleTimeline());

        service.collectMatches(List.of("KR_1"), () -> {
        });

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        String json = matchCaptor.getValue().getItemEventsJson();
        assertTrue(json.contains("ITEM_PURCHASED"));
        assertTrue(json.contains("ITEM_SOLD"));
        assertTrue(json.contains("1055"));
        assertFalse(json.contains("CHAMPION_KILL"));
    }

    @Test
    void collectMatches_savesMatchEvenWhenTimelineFetchFails() {
        when(riotApiClient.getMatchById("KR_1")).thenReturn(sampleMatch("KR_1"));
        when(riotApiClient.getMatchTimeline("KR_1")).thenThrow(new RuntimeException("Riot API down"));

        MatchService.CollectionResult result = service.collectMatches(List.of("KR_1"), () -> {
        });

        assertEquals(1, result.savedCount());
        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertNull(matchCaptor.getValue().getItemEventsJson());
    }
}
