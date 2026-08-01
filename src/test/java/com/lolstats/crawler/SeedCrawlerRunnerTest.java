package com.lolstats.crawler;

import com.lolstats.client.RiotApiClient;
import com.lolstats.client.dto.RiotLeagueSeedEntryResponse;
import com.lolstats.domain.Summoner;
import com.lolstats.service.SummonerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeedCrawlerRunnerTest {

    @Mock
    private RiotApiClient riotApiClient;
    @Mock
    private CrawlerSummonerService crawlerSummonerService;
    @Mock
    private CrawlerMatchBackfillService crawlerMatchBackfillService;

    private SeedCrawlerRunner runner;

    @BeforeEach
    void setUp() {
        runner = new SeedCrawlerRunner(riotApiClient, crawlerSummonerService, crawlerMatchBackfillService);
        lenient().when(riotApiClient.getChallengerLeague(anyString())).thenReturn(List.of());
        lenient().when(riotApiClient.getGrandmasterLeague(anyString())).thenReturn(List.of());
        lenient().when(riotApiClient.getMasterLeague(anyString())).thenReturn(List.of());
        lenient().when(riotApiClient.getLeagueEntries(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of());
    }

    @Test
    void descendsTiersInOrder_challengerThroughIron_includingEmerald() {
        runner.run();

        InOrder inOrder = inOrder(riotApiClient);
        inOrder.verify(riotApiClient).getChallengerLeague(SummonerService.SOLO_QUEUE);
        inOrder.verify(riotApiClient).getGrandmasterLeague(SummonerService.SOLO_QUEUE);
        inOrder.verify(riotApiClient).getMasterLeague(SummonerService.SOLO_QUEUE);
        inOrder.verify(riotApiClient).getLeagueEntries(SummonerService.SOLO_QUEUE, "DIAMOND", "I", 1);
        inOrder.verify(riotApiClient).getLeagueEntries(SummonerService.SOLO_QUEUE, "DIAMOND", "IV", 1);
        // Riot's actual tier list has EMERALD between platinum and diamond (added after the
        // plan's original prose was written) - verified against current league-v4 docs, §0.
        inOrder.verify(riotApiClient).getLeagueEntries(SummonerService.SOLO_QUEUE, "EMERALD", "I", 1);
        inOrder.verify(riotApiClient).getLeagueEntries(SummonerService.SOLO_QUEUE, "PLATINUM", "I", 1);
        inOrder.verify(riotApiClient).getLeagueEntries(SummonerService.SOLO_QUEUE, "GOLD", "I", 1);
        inOrder.verify(riotApiClient).getLeagueEntries(SummonerService.SOLO_QUEUE, "SILVER", "I", 1);
        inOrder.verify(riotApiClient).getLeagueEntries(SummonerService.SOLO_QUEUE, "BRONZE", "I", 1);
        inOrder.verify(riotApiClient).getLeagueEntries(SummonerService.SOLO_QUEUE, "IRON", "I", 1);
        inOrder.verify(riotApiClient).getLeagueEntries(SummonerService.SOLO_QUEUE, "IRON", "IV", 1);
    }

    @Test
    void ironDivisionIVEmptyPage_endsRunNormally_noException() {
        assertDoesNotThrow(() -> runner.run());

        verify(riotApiClient).getLeagueEntries(SummonerService.SOLO_QUEUE, "IRON", "IV", 1);
        // Empty page ends that division - no second page requested anywhere.
        verify(riotApiClient, never()).getLeagueEntries(eq(SummonerService.SOLO_QUEUE), anyString(), anyString(), eq(2));
    }

    @Test
    void nonEmptyPage_processesEntriesAndRequestsNextPage() {
        RiotLeagueSeedEntryResponse entry = new RiotLeagueSeedEntryResponse("p1", "DIAMOND", "I", 10, 1, 1);
        when(riotApiClient.getLeagueEntries(SummonerService.SOLO_QUEUE, "DIAMOND", "I", 1)).thenReturn(List.of(entry));
        Summoner upserted = Summoner.builder().puuid("p1").build();
        when(crawlerSummonerService.upsert(entry)).thenReturn(upserted);

        runner.run();

        verify(crawlerSummonerService).upsert(entry);
        verify(crawlerMatchBackfillService).backfill(upserted);
        verify(riotApiClient).getLeagueEntries(SummonerService.SOLO_QUEUE, "DIAMOND", "I", 2);
    }

    @Test
    void unauthorized_abortsEntireRunImmediately_noDivisionCallsAfter() {
        when(riotApiClient.getMasterLeague(anyString())).thenThrow(HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null));

        assertDoesNotThrow(() -> runner.run()); // caught at the top level, not rethrown to the caller

        verify(riotApiClient, never()).getLeagueEntries(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void forbidden_duringDivisionDescent_abortsRemainingTiers() {
        when(riotApiClient.getLeagueEntries(SummonerService.SOLO_QUEUE, "PLATINUM", "I", 1))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, new byte[0], null));

        assertDoesNotThrow(() -> runner.run());

        verify(riotApiClient, never()).getLeagueEntries(eq(SummonerService.SOLO_QUEUE), eq("GOLD"), anyString(), anyInt());
        verify(riotApiClient, never()).getLeagueEntries(eq(SummonerService.SOLO_QUEUE), eq("IRON"), anyString(), anyInt());
    }

    @Test
    void exceptionProcessingOneEntry_doesNotStopProcessingOthersInThatPage() {
        RiotLeagueSeedEntryResponse bad = new RiotLeagueSeedEntryResponse("bad", "MASTER", "I", 100, 1, 1);
        RiotLeagueSeedEntryResponse good = new RiotLeagueSeedEntryResponse("good", "MASTER", "I", 90, 1, 1);
        when(riotApiClient.getMasterLeague(anyString())).thenReturn(List.of(bad, good));
        when(crawlerSummonerService.upsert(bad)).thenThrow(new RuntimeException("boom"));
        Summoner upsertedGood = Summoner.builder().puuid("good").build();
        when(crawlerSummonerService.upsert(good)).thenReturn(upsertedGood);

        assertDoesNotThrow(() -> runner.run());

        verify(crawlerSummonerService).upsert(good);
        verify(crawlerMatchBackfillService).backfill(upsertedGood);
    }
}
