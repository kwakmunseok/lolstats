package com.lolstats.crawler;

import com.lolstats.client.RiotApiClient;
import com.lolstats.client.dto.RiotAccountResponse;
import com.lolstats.client.dto.RiotLeagueSeedEntryResponse;
import com.lolstats.client.dto.RiotSummonerResponse;
import com.lolstats.domain.Summoner;
import com.lolstats.repository.SummonerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerSummonerServiceTest {

    @Mock
    private SummonerRepository summonerRepository;
    @Mock
    private RiotApiClient riotApiClient;

    private CrawlerSummonerService service;

    @BeforeEach
    void setUp() {
        service = new CrawlerSummonerService(summonerRepository, riotApiClient);
        lenient().when(summonerRepository.save(any(Summoner.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void newPuuid_callsAccountAndSummonerApis_andStampsUpdatedAt() {
        RiotLeagueSeedEntryResponse entry = new RiotLeagueSeedEntryResponse("puuid-new", "CHALLENGER", "I", 1487, 312, 198);
        when(summonerRepository.findByPuuid("puuid-new")).thenReturn(Optional.empty());
        when(riotApiClient.getAccountByPuuid("puuid-new"))
                .thenReturn(new RiotAccountResponse("puuid-new", "NewPlayer", "KR1"));
        when(riotApiClient.getSummonerByPuuid("puuid-new"))
                .thenReturn(new RiotSummonerResponse("puuid-new", 4568, 612));

        Summoner result = service.upsert(entry);

        assertEquals("NewPlayer", result.getGameName());
        assertEquals("KR1", result.getTagLine());
        assertEquals(4568, result.getProfileIconId());
        assertEquals(612, result.getSummonerLevel());
        assertEquals("CHALLENGER", result.getTier());
        assertEquals(312, result.getWins());
        assertNotNull(result.getUpdatedAt());
        verify(riotApiClient).getAccountByPuuid("puuid-new");
        verify(riotApiClient).getSummonerByPuuid("puuid-new");
    }

    @Test
    void existingPuuid_updatesLeagueFieldsOnly_zeroRiotCalls_leavesUpdatedAtUntouched() {
        Instant original = Instant.now().minus(10, ChronoUnit.DAYS);
        Summoner existing = Summoner.builder()
                .id(1L)
                .puuid("puuid-known")
                .gameName("OldName")
                .tagLine("KR1")
                .tier("GOLD")
                .rank("IV")
                .leaguePoints(10)
                .wins(5)
                .losses(5)
                .updatedAt(original)
                .build();
        RiotLeagueSeedEntryResponse entry = new RiotLeagueSeedEntryResponse("puuid-known", "PLATINUM", "II", 55, 40, 20);
        when(summonerRepository.findByPuuid("puuid-known")).thenReturn(Optional.of(existing));

        Summoner result = service.upsert(entry);

        assertEquals("PLATINUM", result.getTier());
        assertEquals("II", result.getRank());
        assertEquals(55, result.getLeaguePoints());
        assertEquals(40, result.getWins());
        assertEquals(20, result.getLosses());
        assertEquals("OldName", result.getGameName()); // untouched
        assertEquals(original, result.getUpdatedAt()); // untouched
        verifyNoInteractions(riotApiClient);
    }
}
