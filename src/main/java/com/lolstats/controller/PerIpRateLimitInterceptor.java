package com.lolstats.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

// Fixed-window per-IP limit on the two endpoints that actually spend Riot's shared budget
// (search, refresh) - Bucket4j already protects Riot globally, this is the "one user alone
// can't hog it" layer (PROJECT_PLAN.md §8 리스크).
@Slf4j
@Component
public class PerIpRateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "ratelimit:ip:";
    private static final int LIMIT = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;

    public PerIpRateLimitInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!tryAcquire(request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다. 잠시 후 다시 시도해주세요");
        }
        return true;
    }

    // Fail-open on Redis errors (PROJECT_PLAN.md §8) - without Redis there's no way to count,
    // so requests pass through unrestricted rather than being blocked entirely.
    private boolean tryAcquire(String ip) {
        try {
            String key = KEY_PREFIX + ip;
            Long count = redisTemplate.opsForValue().increment(key);
            // Only the request that takes the count from 0->1 sets the TTL - setting it
            // unconditionally means a crash between INCR and EXPIRE leaves a TTL-less key that
            // never resets, permanently rate-limiting that IP (the fixed-window atomicity gap
            // this Task exists to demonstrate - PHASE2_PLAN.md Task 6).
            if (count != null && count == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
            return count == null || count <= LIMIT;
        } catch (DataAccessException e) {
            log.warn("Redis unavailable - allowing request through without a per-IP rate check", e);
            return true;
        }
    }
}
