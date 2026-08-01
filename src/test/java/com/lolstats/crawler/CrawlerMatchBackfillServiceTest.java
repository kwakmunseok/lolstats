package com.lolstats.crawler;

import com.lolstats.domain.Summoner;
import com.lolstats.repository.SummonerRepository;
import com.lolstats.service.MatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerMatchBackfillServiceTest {

    @Mock
    private MatchService matchService;
    @Mock
    private SummonerRepository summonerRepository;

    private CrawlerMatchBackfillService service;

    @BeforeEach
    void setUp() {
        service = new CrawlerMatchBackfillService(matchService, summonerRepository);
    }

    @Test
    void alreadyBackfilled_skipsPlanningAndCollectionEntirely() {
        Summoner summoner = Summoner.builder()
                .puuid("puuid-1")
                .crawlerBackfilledAt(Instant.now())
                .build();

        service.backfill(summoner);

        verifyNoInteractions(matchService);
        verify(summonerRepository, never()).save(any());
    }

    @Test
    void fullyCollected_marksBackfilledAt() {
        Summoner summoner = Summoner.builder().puuid("puuid-1").build();
        when(matchService.planCollection("puuid-1"))
                .thenReturn(new MatchService.CollectionPlan(2, List.of("KR_1", "KR_2")));
        when(matchService.collectMatches(eq(List.of("KR_1", "KR_2")), any()))
                .thenReturn(new MatchService.CollectionResult(2, true));

        service.backfill(summoner);

        assertNotNull(summoner.getCrawlerBackfilledAt());
        verify(summonerRepository).save(summoner);
    }

    @Test
    void partiallyCollected_dueTo429_doesNotMarkBackfilledAt() {
        Summoner summoner = Summoner.builder().puuid("puuid-1").build();
        when(matchService.planCollection("puuid-1"))
                .thenReturn(new MatchService.CollectionPlan(3, List.of("KR_1", "KR_2", "KR_3")));
        when(matchService.collectMatches(eq(List.of("KR_1", "KR_2", "KR_3")), any()))
                .thenReturn(new MatchService.CollectionResult(1, false));

        service.backfill(summoner);

        assertNull(summoner.getCrawlerBackfilledAt());
        verify(summonerRepository, never()).save(any());
    }
}
