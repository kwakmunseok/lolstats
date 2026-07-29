package com.lolstats.client;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Times the shared rate-limit bucket directly (no network, no Spring context) instead of
// hitting a real Riot endpoint 25 times - the same technique used for the live check during
// Task 1, now kept as a permanent regression test (PHASE2_PLAN.md Task 8).
class RiotApiConfigTest {

    @Test
    void bucket_allowsBurstThenBlocksUntilRefill() {
        Bucket bucket = new RiotApiConfig().riotApiRateLimitBucket();

        long firstTwentyMs = timeConsume(bucket, 20);
        long nextFiveMs = timeConsume(bucket, 5);

        assertTrue(firstTwentyMs < 200, "first 20 calls should drain the burst almost instantly, took " + firstTwentyMs + "ms");
        assertTrue(nextFiveMs >= 150, "21st call onward should visibly wait for refill (20/s => ~50ms/token), took " + nextFiveMs + "ms");
    }

    private long timeConsume(Bucket bucket, int count) {
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            bucket.asBlocking().consumeUninterruptibly(1);
        }
        return (System.nanoTime() - start) / 1_000_000;
    }
}
