package com.lolstats.crawler;

import com.lolstats.domain.Summoner;
import com.lolstats.repository.SummonerRepository;
import com.lolstats.service.MatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

// Wraps MatchService.planCollection/collectMatches with the crawler's checkpoint marker
// (CRAWLER_PLAN.md §3 Task 2). Deliberately bypasses MatchCollectionQueue (Redis
// collecting: flag) - the crawler is the only worker touching these puuids, so there's
// nothing to coordinate with.
@Slf4j
@Service
public class CrawlerMatchBackfillService {

    private final MatchService matchService;
    private final SummonerRepository summonerRepository;

    public CrawlerMatchBackfillService(MatchService matchService, SummonerRepository summonerRepository) {
        this.matchService = matchService;
        this.summonerRepository = summonerRepository;
    }

    public void backfill(Summoner summoner) {
        if (summoner.getCrawlerBackfilledAt() != null) {
            // DoD #4 (CRAWLER_PLAN.md §4) is checked via this line: a restart must skip
            // already-backfilled puuids without calling Riot again.
            log.info("puuid {} already backfilled at {} - skipping match collection",
                    summoner.getPuuid(), summoner.getCrawlerBackfilledAt());
            return;
        }

        MatchService.CollectionPlan plan = matchService.planCollection(summoner.getPuuid());
        MatchService.CollectionResult result = matchService.collectMatches(plan.missingMatchIds(), () -> {
        });

        // Only mark done when every missing match actually got saved - a 429 cutting the run
        // short must stay eligible for retry next crawl, or it never gets filled in (§5
        // "collectMatches 완료 판정").
        if (result.complete()) {
            summoner.setCrawlerBackfilledAt(Instant.now());
            summonerRepository.save(summoner);
        }
    }
}
