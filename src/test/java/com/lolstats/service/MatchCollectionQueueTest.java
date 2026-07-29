package com.lolstats.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// process() is package-private specifically so it can be driven directly here instead of
// racing the real background thread (which never starts anyway - @PostConstruct only fires
// under a live Spring context, not in a plain Mockito unit test).
@ExtendWith(MockitoExtension.class)
class MatchCollectionQueueTest {

    @Mock
    private MatchService matchService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private MatchCollectionQueue queue;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        queue = new MatchCollectionQueue(matchService, redisTemplate);
    }

    @Test
    void enqueue_acquiresLockWithTotalCountAndFiveMinuteTtl() {
        when(valueOperations.setIfAbsent("collecting:puuid-1", "15", Duration.ofMinutes(5)))
                .thenReturn(true);

        queue.enqueue("puuid-1", 15);

        verify(valueOperations).setIfAbsent("collecting:puuid-1", "15", Duration.ofMinutes(5));
    }

    @Test
    void isCollecting_and_totalCount_readFromRedis() {
        when(redisTemplate.hasKey("collecting:puuid-1")).thenReturn(true);
        when(valueOperations.get("collecting:puuid-1")).thenReturn("12");

        assertTrue(queue.isCollecting("puuid-1"));
        assertTrue(queue.totalCount("puuid-1").equals(12));
    }

    @Test
    void isCollecting_false_whenKeyMissing() {
        when(redisTemplate.hasKey("collecting:puuid-1")).thenReturn(false);

        assertFalse(queue.isCollecting("puuid-1"));
    }

    @Test
    void process_deletesRedisKeyAfterSuccessfulCollection() {
        when(matchService.planCollection("puuid-1"))
                .thenReturn(new MatchService.CollectionPlan(2, List.of("KR_1", "KR_2")));

        queue.process("puuid-1");

        verify(matchService).collectMatches(eq(List.of("KR_1", "KR_2")), org.mockito.ArgumentMatchers.any());
        verify(redisTemplate).delete("collecting:puuid-1");
    }

    @Test
    void process_renewsTtlOnceForEachMatchSaved() {
        when(matchService.planCollection("puuid-1"))
                .thenReturn(new MatchService.CollectionPlan(3, List.of("KR_1", "KR_2", "KR_3")));
        // Simulate MatchService actually saving 3 matches, invoking the heartbeat callback each time.
        doAnswer(invocation -> {
            Runnable afterEachSave = invocation.getArgument(1);
            afterEachSave.run();
            afterEachSave.run();
            afterEachSave.run();
            return null;
        }).when(matchService).collectMatches(eq(List.of("KR_1", "KR_2", "KR_3")), org.mockito.ArgumentMatchers.any());

        queue.process("puuid-1");

        verify(redisTemplate, times(3)).expire("collecting:puuid-1", Duration.ofMinutes(5));
    }

    @Test
    void process_pausesOnUnauthorized_andDropsSubsequentJobsWithoutCallingRiot() {
        when(matchService.planCollection("puuid-1"))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null));

        queue.process("puuid-1");

        assertTrue(queue.isPaused());
        verify(redisTemplate).delete("collecting:puuid-1");

        queue.process("puuid-2");

        verify(matchService, never()).planCollection("puuid-2");
        verify(redisTemplate).delete("collecting:puuid-2");
    }
}
