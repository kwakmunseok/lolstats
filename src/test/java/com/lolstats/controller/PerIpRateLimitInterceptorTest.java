package com.lolstats.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerIpRateLimitInterceptorTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private PerIpRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        interceptor = new PerIpRateLimitInterceptor(redisTemplate);
    }

    @Test
    void preHandle_allowsRequestsAtOrUnderLimit() {
        for (long count = 1; count <= 10; count++) {
            when(valueOperations.increment("ratelimit:ip:127.0.0.1")).thenReturn(count);
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }
    }

    @Test
    void preHandle_rejectsWithTooManyRequests_whenOverLimit() {
        when(valueOperations.increment("ratelimit:ip:127.0.0.1")).thenReturn(11L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request, response, new Object()));
        assertTrue(ex.getStatusCode().value() == 429);
    }

    @Test
    void preHandle_setsTtlOnlyOnFirstRequestInWindow() {
        // The atomicity-gap guard this Task exists for: EXPIRE must fire exactly once, only
        // when INCR takes the counter from 0 to 1 - not on every request in the window.
        when(valueOperations.increment("ratelimit:ip:127.0.0.1")).thenReturn(1L, 2L, 3L);

        interceptor.preHandle(request, response, new Object());
        interceptor.preHandle(request, response, new Object());
        interceptor.preHandle(request, response, new Object());

        verify(redisTemplate, times(1)).expire("ratelimit:ip:127.0.0.1", Duration.ofMinutes(1));
    }

    @Test
    void preHandle_failsOpen_whenRedisUnavailable() {
        when(valueOperations.increment("ratelimit:ip:127.0.0.1")).thenThrow(new QueryTimeoutException("redis down"));

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(redisTemplate, never()).expire(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }
}
