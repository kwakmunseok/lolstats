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
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private SummonerService service;

    @BeforeEach
    void setUp() {
        service = new SummonerService(summonerRepository, searchCountRepository, riotApiClient, redisTemplate, 10);

        // recordSearch() (called by every findOrFetch()/refresh() test) always tries the
        // ZINCRBY ranking update - stub it so those tests don't need to know about Redis.
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

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

    @Test
    void refresh_refetchesFromRiotEvenThoughCacheIsFresh() {
        Summoner fresh = Summoner.builder()
                .id(1L).puuid("puuid-1").gameName("Hide on bush").tagLine("KR1")
                .updatedAt(Instant.now()) // well within TTL - a plain findOrFetch would skip Riot entirely
                .build();
        when(summonerRepository.findById(1L)).thenReturn(Optional.of(fresh));
        when(riotApiClient.getAccountByRiotId("Hide on bush", "KR1"))
                .thenReturn(new RiotAccountResponse("puuid-1", "Hide on bush", "KR1"));
        when(summonerRepository.findByPuuid("puuid-1")).thenReturn(Optional.of(fresh));
        when(riotApiClient.getSummonerByPuuid("puuid-1"))
                .thenReturn(new RiotSummonerResponse("puuid-1", 4568, 613));
        when(riotApiClient.getLeagueEntriesByPuuid("puuid-1")).thenReturn(List.of());
        when(searchCountRepository.findById(1L)).thenReturn(Optional.empty());

        Summoner result = service.refresh(1L);

        assertEquals(613, result.getSummonerLevel());
        verify(riotApiClient).getAccountByRiotId("Hide on bush", "KR1");
    }

    @Test
    void refresh_unknownSummonerId_throwsNotFound() {
        when(summonerRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.refresh(404L));
        verifyNoInteractions(riotApiClient);
    }

    @Test
    void isRefreshCoolingDown_readsCooldownKey() {
        when(redisTemplate.hasKey("cooldown:1")).thenReturn(true);
        when(redisTemplate.hasKey("cooldown:2")).thenReturn(false);

        assertTrue(service.isRefreshCoolingDown(1L));
        assertFalse(service.isRefreshCoolingDown(2L));
    }

    @Test
    void startRefreshCooldown_setsSixtySecondTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.startRefreshCooldown(1L);

        verify(valueOperations).set("cooldown:1", "1", Duration.ofSeconds(60));
    }

    @Test
    void isRefreshCoolingDown_failsOpen_whenRedisUnavailable() {
        when(redisTemplate.hasKey("cooldown:1")).thenThrow(new QueryTimeoutException("redis down"));

        assertFalse(service.isRefreshCoolingDown(1L));
    }

    @Test
    void startRefreshCooldown_doesNotThrow_whenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new QueryTimeoutException("redis down"))
                .when(valueOperations).set(any(), any(), any(Duration.class));

        service.startRefreshCooldown(1L); // must not throw
    }

    @Test
    void recordSearch_ranksSummonerAndFailsOpen_whenRedisUnavailable() {
        when(summonerRepository.findByGameNameAndTagLine("Hide on bush", "KR1")).thenReturn(List.of());
        when(riotApiClient.getAccountByRiotId("Hide on bush", "KR1"))
                .thenReturn(new RiotAccountResponse("puuid-1", "Hide on bush", "KR1"));
        when(summonerRepository.findByPuuid("puuid-1")).thenReturn(Optional.empty());
        when(riotApiClient.getSummonerByPuuid("puuid-1")).thenReturn(new RiotSummonerResponse("puuid-1", 1, 30));
        when(riotApiClient.getLeagueEntriesByPuuid("puuid-1")).thenReturn(List.of());
        when(searchCountRepository.findById(99L)).thenReturn(Optional.empty());
        when(zSetOperations.incrementScore(eq("search_rank"), eq("99"), eq(1.0)))
                .thenThrow(new QueryTimeoutException("redis down"));

        Summoner result = service.findOrFetch("Hide on bush", "KR1"); // must not throw despite Redis failing

        assertEquals("puuid-1", result.getPuuid());
        verify(searchCountRepository).save(any());
    }

    @Test
    void popular_prefersRedisRankingOverDb_preservingOrder() {
        Summoner second = Summoner.builder().id(2L).puuid("p2").gameName("Second").tagLine("KR1").build();
        Summoner first = Summoner.builder().id(1L).puuid("p1").gameName("First").tagLine("KR1").build();
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange("search_rank", 0, 4)).thenReturn(new LinkedHashSet<>(List.of("1", "2")));
        when(summonerRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(second, first));

        List<Summoner> result = service.popular(5);

        assertEquals(List.of("p1", "p2"), result.stream().map(Summoner::getPuuid).toList());
        verifyNoInteractions(searchCountRepository);
    }

    @Test
    void popular_fallsBackToDb_whenRedisThrows() {
        Summoner top = Summoner.builder().id(1L).puuid("p1").gameName("Faker").tagLine("KR1").build();
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(eq("search_rank"), any(Long.class), any(Long.class)))
                .thenThrow(new QueryTimeoutException("redis down"));
        when(searchCountRepository.findPopularSummoners(any())).thenReturn(List.of(top));

        List<Summoner> result = service.popular(5);

        assertEquals(1, result.size());
        assertEquals("p1", result.get(0).getPuuid());
    }
}
