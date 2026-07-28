package com.lolstats.service;

import com.lolstats.client.RiotApiClient;
import com.lolstats.client.dto.RiotAccountResponse;
import com.lolstats.client.dto.RiotLeagueEntryResponse;
import com.lolstats.client.dto.RiotSummonerResponse;
import com.lolstats.domain.Summoner;
import com.lolstats.repository.SearchCountRepository;
import com.lolstats.repository.SummonerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummonerServiceTest {

    @Mock
    private SummonerRepository summonerRepository;
    @Mock
    private SearchCountRepository searchCountRepository;
    @Mock
    private RiotApiClient riotApiClient;

    private SummonerService service;

    @BeforeEach
    void setUp() {
        service = new SummonerService(summonerRepository, searchCountRepository, riotApiClient, 10);

        // Mimics JPA assigning a generated id on first insert; tests override with
        // pre-set ids where a row is meant to already exist.
        lenient().when(summonerRepository.save(any(Summoner.class))).thenAnswer(invocation -> {
            Summoner saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(99L);
            }
            return saved;
        });
    }

    @Test
    void freshCacheHit_doesNotCallRiotApi() {
        Summoner cached = Summoner.builder()
                .id(1L)
                .puuid("puuid-1")
                .gameName("Hide on bush")
                .tagLine("KR1")
                .updatedAt(Instant.now())
                .build();
        when(summonerRepository.findByGameNameAndTagLine("Hide on bush", "KR1"))
                .thenReturn(List.of(cached));
        when(searchCountRepository.findById(1L)).thenReturn(Optional.empty());

        Summoner result = service.findOrFetch("Hide on bush", "KR1");

        assertEquals("puuid-1", result.getPuuid());
        verifyNoInteractions(riotApiClient);
    }

    @Test
    void expiredCache_refetchesFromRiotAndUpserts() {
        Summoner stale = Summoner.builder()
                .id(1L)
                .puuid("puuid-1")
                .gameName("Hide on bush")
                .tagLine("KR1")
                .updatedAt(Instant.now().minus(20, ChronoUnit.MINUTES))
                .build();
        when(summonerRepository.findByGameNameAndTagLine("Hide on bush", "KR1"))
                .thenReturn(List.of(stale));
        when(riotApiClient.getAccountByRiotId("Hide on bush", "KR1"))
                .thenReturn(new RiotAccountResponse("puuid-1", "Hide on bush", "KR1"));
        when(summonerRepository.findByPuuid("puuid-1")).thenReturn(Optional.of(stale));
        when(riotApiClient.getSummonerByPuuid("puuid-1"))
                .thenReturn(new RiotSummonerResponse("puuid-1", 4568, 612));
        when(riotApiClient.getLeagueEntriesByPuuid("puuid-1"))
                .thenReturn(List.of(new RiotLeagueEntryResponse("RANKED_SOLO_5x5", "CHALLENGER", "I", 1487, 312, 198)));
        when(searchCountRepository.findById(1L)).thenReturn(Optional.empty());

        Summoner result = service.findOrFetch("Hide on bush", "KR1");

        assertEquals("CHALLENGER", result.getTier());
        assertEquals(4568, result.getProfileIconId());
        verify(riotApiClient).getAccountByRiotId("Hide on bush", "KR1");
        verify(riotApiClient).getSummonerByPuuid("puuid-1");
        verify(riotApiClient).getLeagueEntriesByPuuid("puuid-1");
    }

    @Test
    void unranked_leavesTierFieldsNull() {
        when(summonerRepository.findByGameNameAndTagLine("NewPlayer", "KR1"))
                .thenReturn(List.of());
        when(riotApiClient.getAccountByRiotId("NewPlayer", "KR1"))
                .thenReturn(new RiotAccountResponse("puuid-2", "NewPlayer", "KR1"));
        when(summonerRepository.findByPuuid("puuid-2")).thenReturn(Optional.empty());
        when(riotApiClient.getSummonerByPuuid("puuid-2"))
                .thenReturn(new RiotSummonerResponse("puuid-2", 1, 30));
        when(riotApiClient.getLeagueEntriesByPuuid("puuid-2")).thenReturn(List.of());
        when(searchCountRepository.findById(99L)).thenReturn(Optional.empty());

        Summoner result = service.findOrFetch("NewPlayer", "KR1");

        assertNull(result.getTier());
        assertNull(result.getRank());
        assertNull(result.getLeaguePoints());
    }

    @Test
    void nameChangedOwner_resolvesByPuuidNotName() {
        // Name lookup hits the row for the *previous* owner of "Hide on bush#KR1".
        // Riot's account lookup returns the *current* owner's puuid, which belongs to
        // a different row - the upsert must key off that puuid, not the searched name.
        Summoner oldOwnerRow = Summoner.builder()
                .id(1L)
                .puuid("puuid-old-owner")
                .gameName("Hide on bush")
                .tagLine("KR1")
                .updatedAt(Instant.now().minus(20, ChronoUnit.MINUTES))
                .build();
        Summoner newOwnerRow = Summoner.builder()
                .id(2L)
                .puuid("puuid-new-owner")
                .updatedAt(Instant.now().minus(20, ChronoUnit.MINUTES))
                .build();

        when(summonerRepository.findByGameNameAndTagLine("Hide on bush", "KR1"))
                .thenReturn(List.of(oldOwnerRow));
        when(riotApiClient.getAccountByRiotId("Hide on bush", "KR1"))
                .thenReturn(new RiotAccountResponse("puuid-new-owner", "Hide on bush", "KR1"));
        when(summonerRepository.findByPuuid("puuid-new-owner")).thenReturn(Optional.of(newOwnerRow));
        when(riotApiClient.getSummonerByPuuid("puuid-new-owner"))
                .thenReturn(new RiotSummonerResponse("puuid-new-owner", 10, 50));
        when(riotApiClient.getLeagueEntriesByPuuid("puuid-new-owner")).thenReturn(List.of());
        when(searchCountRepository.findById(2L)).thenReturn(Optional.empty());

        Summoner result = service.findOrFetch("Hide on bush", "KR1");

        assertEquals("puuid-new-owner", result.getPuuid());
        assertEquals(2L, result.getId());
    }

    @Test
    void autocomplete_dedupesSameDisplayNameKeepingMostRecentlySearched() {
        // Repository already orders by last_searched_at DESC, so the mock returns the
        // most-recently-searched (new owner) row first.
        Summoner newOwner = Summoner.builder().id(2L).puuid("puuid-new").gameName("Hide on bush").tagLine("KR1").build();
        Summoner oldOwner = Summoner.builder().id(1L).puuid("puuid-old").gameName("Hide on bush").tagLine("KR1").build();
        when(searchCountRepository.findSummonersByGameNamePrefix(eq("Hide"), any()))
                .thenReturn(List.of(newOwner, oldOwner));

        List<Summoner> result = service.autocomplete("Hide", 10);

        assertEquals(1, result.size());
        assertEquals("puuid-new", result.get(0).getPuuid());
    }

    @Test
    void autocomplete_respectsLimitAfterDedup() {
        List<Summoner> candidates = List.of(
                Summoner.builder().id(1L).puuid("p1").gameName("Faker1").tagLine("KR1").build(),
                Summoner.builder().id(2L).puuid("p2").gameName("Faker2").tagLine("KR1").build(),
                Summoner.builder().id(3L).puuid("p3").gameName("Faker3").tagLine("KR1").build());
        when(searchCountRepository.findSummonersByGameNamePrefix(eq("Faker"), any())).thenReturn(candidates);

        List<Summoner> result = service.autocomplete("Faker", 2);

        assertEquals(2, result.size());
    }

    @Test
    void popular_delegatesToRepository() {
        Summoner top = Summoner.builder().id(1L).puuid("p1").gameName("Faker").tagLine("KR1").build();
        when(searchCountRepository.findPopularSummoners(any())).thenReturn(List.of(top));

        List<Summoner> result = service.popular(5);

        assertEquals(1, result.size());
        assertEquals("Faker", result.get(0).getGameName());
    }
}
