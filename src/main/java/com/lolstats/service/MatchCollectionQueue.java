package com.lolstats.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// Single worker thread draining an in-memory queue - deliberately not per-match @Async
// fan-out, which would let every in-flight match block its own thread on Bucket4j at once
// (PHASE2_PLAN.md Task 3 결정 사항). Redis collecting:{puuid} does double duty: its existence
// is the "still collecting" flag the API polls, and its value carries totalCount so a
// SETNX-style acquire also dedupes concurrent searches for the same puuid.
@Slf4j
@Component
public class MatchCollectionQueue {

    private static final String KEY_PREFIX = "collecting:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final MatchService matchService;
    private final StringRedisTemplate redisTemplate;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    // Set once a 401/403 is seen (expired Dev Key) - retrying won't fix a rejected key, so
    // queued jobs are dropped without calling Riot until the app restarts with a valid key.
    private volatile boolean paused = false;
    private Thread worker;

    public MatchCollectionQueue(MatchService matchService, StringRedisTemplate redisTemplate) {
        this.matchService = matchService;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    void start() {
        worker = new Thread(this::runLoop, "match-collection-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    void stop() {
        worker.interrupt();
    }

    public void enqueue(String puuid, int totalCount) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + puuid, String.valueOf(totalCount), TTL);
        if (Boolean.TRUE.equals(acquired)) {
            queue.offer(puuid);
        }
    }

    public boolean isCollecting(String puuid) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + puuid));
    }

    public Integer totalCount(String puuid) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + puuid);
        return value == null ? null : Integer.valueOf(value);
    }

    public boolean isPaused() {
        return paused;
    }

    private void runLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                process(queue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Package-private so tests can drive one job synchronously instead of racing the
    // background thread.
    void process(String puuid) {
        if (paused) {
            log.warn("collection paused (Riot key was rejected earlier) - dropping queued puuid {}", puuid);
            redisTemplate.delete(KEY_PREFIX + puuid);
            return;
        }
        try {
            MatchService.CollectionPlan plan = matchService.planCollection(puuid);
            matchService.collectMatches(plan.missingMatchIds(), () -> redisTemplate.expire(KEY_PREFIX + puuid, TTL));
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            paused = true;
            log.error("Riot API key rejected ({}) - pausing background collection until restart", e.getStatusCode());
        } catch (Exception e) {
            // Anything else (a bad match payload, a transient DB error, ...) must not kill
            // this thread - runLoop()'s catch only handles InterruptedException, so an
            // uncaught exception here would silently end all future background collection
            // until restart. Log and move on to the next queued puuid instead.
            log.error("Unexpected error collecting matches for puuid {}", puuid, e);
        } finally {
            redisTemplate.delete(KEY_PREFIX + puuid);
        }
    }
}
