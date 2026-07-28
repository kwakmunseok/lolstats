package com.lolstats.service;

import com.lolstats.client.RiotApiClient;
import com.lolstats.client.dto.RiotMatchResponse;
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
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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

    private MatchService service;

    @BeforeEach
    void setUp() {
        service = new MatchService(riotApiClient, matchRepository, matchParticipantRepository, JsonMapper.builder().build());
        lenient().when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

    @Test
    void skipsMatchIdsAlreadyInDb() {
        when(riotApiClient.getMatchIdsByPuuid("puuid-1", 20))
                .thenReturn(List.of("KR_1", "KR_2", "KR_3"));
        when(matchRepository.existsByRiotMatchId("KR_1")).thenReturn(true);
        when(matchRepository.existsByRiotMatchId("KR_2")).thenReturn(false);
        when(matchRepository.existsByRiotMatchId("KR_3")).thenReturn(false);
        when(riotApiClient.getMatchById("KR_2")).thenReturn(sampleMatch("KR_2"));
        when(riotApiClient.getMatchById("KR_3")).thenReturn(sampleMatch("KR_3"));

        service.collectRecentMatches("puuid-1");

        verify(riotApiClient, never()).getMatchById("KR_1");
        verify(riotApiClient).getMatchById("KR_2");
        verify(riotApiClient).getMatchById("KR_3");
    }

    @Test
    void limitsDetailFetchesToFivePerSearch() {
        List<String> ids = List.of("KR_1", "KR_2", "KR_3", "KR_4", "KR_5", "KR_6", "KR_7");
        when(riotApiClient.getMatchIdsByPuuid("puuid-1", 20)).thenReturn(ids);
        // filter().limit(5) short-circuits lazily, so existsByRiotMatchId is never even
        // called for KR_6/KR_7 - both stubs below are lenient for that reason.
        for (String id : ids) {
            lenient().when(matchRepository.existsByRiotMatchId(id)).thenReturn(false);
            lenient().when(riotApiClient.getMatchById(id)).thenReturn(sampleMatch(id));
        }

        service.collectRecentMatches("puuid-1");

        verify(riotApiClient, times(5)).getMatchById(any());
        verify(riotApiClient, never()).getMatchById("KR_6");
        verify(riotApiClient, never()).getMatchById("KR_7");
    }

    @Test
    void savesMatchAndParticipantsWithMappedFields() {
        when(riotApiClient.getMatchIdsByPuuid("puuid-1", 20)).thenReturn(List.of("KR_1"));
        when(matchRepository.existsByRiotMatchId("KR_1")).thenReturn(false);
        when(riotApiClient.getMatchById("KR_1")).thenReturn(sampleMatch("KR_1"));

        service.collectRecentMatches("puuid-1");

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
    void stopsQuietlyOn429_keepsAlreadySavedMatches() {
        when(riotApiClient.getMatchIdsByPuuid("puuid-1", 20))
                .thenReturn(List.of("KR_1", "KR_2", "KR_3"));
        when(matchRepository.existsByRiotMatchId(any())).thenReturn(false);
        when(riotApiClient.getMatchById("KR_1")).thenReturn(sampleMatch("KR_1"));
        when(riotApiClient.getMatchById("KR_2"))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null));

        service.collectRecentMatches("puuid-1");

        verify(riotApiClient).getMatchById("KR_1");
        verify(riotApiClient).getMatchById("KR_2");
        verify(riotApiClient, never()).getMatchById("KR_3");
        verify(matchRepository, times(1)).save(any(Match.class));
    }
}
