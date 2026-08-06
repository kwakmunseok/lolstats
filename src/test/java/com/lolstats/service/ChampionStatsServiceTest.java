package com.lolstats.service;

import com.lolstats.domain.MatchParticipant;
import com.lolstats.dto.ChampionStatRow;
import com.lolstats.dto.ChampionStatsResponse;
import com.lolstats.repository.MatchParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChampionStatsServiceTest {

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    private ChampionStatsService service;

    @BeforeEach
    void setUp() {
        service = new ChampionStatsService(matchParticipantRepository);
    }

    private static MatchParticipant participant(Integer championId, int kills, int deaths, int assists, boolean win) {
        return MatchParticipant.builder()
                .championId(championId).kills(kills).deaths(deaths).assists(assists).win(win)
                .build();
    }

    @Test
    void stats_computesOverallWinRateAndRecentFormInGivenOrder() {
        // Repository already returns game_creation DESC (query derivation) - service trusts that order as-is.
        when(matchParticipantRepository.findByPuuidAndMatch_QueueTypeInOrderByMatch_GameCreationDesc(
                eq("puuid-1"), eq(MatchService.RIFT_QUEUE_TYPES)))
                .thenReturn(List.of(
                        participant(1, 5, 3, 4, true),
                        participant(1, 2, 5, 1, true),
                        participant(2, 1, 6, 2, false)));

        ChampionStatsResponse stats = service.stats("puuid-1");

        assertEquals(3, stats.games());
        assertEquals(2.0 / 3, stats.overallWinRate());
        assertEquals(List.of(true, true, false), stats.recentForm());
    }

    @Test
    void stats_groupsByChampionWithWinRateAndAveragedKda() {
        when(matchParticipantRepository.findByPuuidAndMatch_QueueTypeInOrderByMatch_GameCreationDesc(
                eq("puuid-1"), eq(MatchService.RIFT_QUEUE_TYPES)))
                .thenReturn(List.of(
                        participant(103, 10, 2, 8, true),
                        participant(103, 4, 4, 4, false),
                        participant(7, 6, 3, 3, true)));

        ChampionStatsResponse stats = service.stats("puuid-1");

        ChampionStatRow ahri = stats.perChampion().stream().filter(r -> r.championId() == 103).findFirst().orElseThrow();
        assertEquals(2, ahri.games());
        assertEquals(1, ahri.wins());
        assertEquals(0.5, ahri.winRate());
        // (10+8 + 4+4) / (2+4) = 26/6
        assertEquals(26.0 / 6, ahri.avgKda(), 1e-9);

        ChampionStatRow leblanc = stats.perChampion().stream().filter(r -> r.championId() == 7).findFirst().orElseThrow();
        assertEquals(1, leblanc.games());
        assertEquals(9.0 / 3, leblanc.avgKda(), 1e-9);
    }

    @Test
    void stats_zeroDeaths_doesNotDivideByZero() {
        when(matchParticipantRepository.findByPuuidAndMatch_QueueTypeInOrderByMatch_GameCreationDesc(
                eq("puuid-1"), eq(MatchService.RIFT_QUEUE_TYPES)))
                .thenReturn(List.of(participant(103, 6, 0, 4, true)));

        ChampionStatsResponse stats = service.stats("puuid-1");

        ChampionStatRow row = stats.perChampion().get(0);
        // sum(deaths) == 0 - treated as kills+assists rather than dividing by zero.
        assertEquals(10.0, row.avgKda(), 1e-9);
        assertTrue(Double.isFinite(row.avgKda()));
    }

    @Test
    void stats_noMatches_returnsEmptyResponseWithoutDivideByZero() {
        when(matchParticipantRepository.findByPuuidAndMatch_QueueTypeInOrderByMatch_GameCreationDesc(
                eq("puuid-1"), eq(MatchService.RIFT_QUEUE_TYPES)))
                .thenReturn(List.of());

        ChampionStatsResponse stats = service.stats("puuid-1");

        assertEquals(0, stats.games());
        assertEquals(0.0, stats.overallWinRate());
        assertTrue(stats.recentForm().isEmpty());
        assertTrue(stats.perChampion().isEmpty());
    }
}
