package com.lolstats.crawler;

import com.lolstats.client.RiotApiClient;
import com.lolstats.client.dto.RiotLeagueSeedEntryResponse;
import com.lolstats.domain.Summoner;
import com.lolstats.service.SummonerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.function.Supplier;

// Local-only bootstrap tool (CRAWLER_PLAN.md) - run via
// `./gradlew bootRun --args='--spring.profiles.active=dev,crawler'`. Never deployed to EC2;
// application-crawler.yml turns off the web server since this is a batch job, not an API.
//
// Descent order: challenger -> grandmaster -> master (one call each, no paging) -> diamond
// down to iron, division I->IV, paged until an empty page ends that division. Tier list
// verified against current league-v4 docs (CRAWLER_PLAN.md §0) - includes EMERALD between
// PLATINUM and DIAMOND, which the plan's original prose omitted (added post-2023, easy to
// miss from memory alone).
//
// No checkpoint table (§5, intentional) - every run re-descends from challenger. Layer①
// (CrawlerSummonerService) makes zero Riot calls for puuids already known, and layer②③
// (CrawlerMatchBackfillService) skips anything already backfilled, so re-descending is cheap.
@Slf4j
@Component
@Profile("crawler")
public class SeedCrawlerRunner implements CommandLineRunner {

    private static final List<String> DIVISION_TIERS =
            List.of("DIAMOND", "EMERALD", "PLATINUM", "GOLD", "SILVER", "BRONZE", "IRON");
    private static final List<String> DIVISIONS = List.of("I", "II", "III", "IV");

    private final RiotApiClient riotApiClient;
    private final CrawlerSummonerService crawlerSummonerService;
    private final CrawlerMatchBackfillService crawlerMatchBackfillService;

    public SeedCrawlerRunner(
            RiotApiClient riotApiClient,
            CrawlerSummonerService crawlerSummonerService,
            CrawlerMatchBackfillService crawlerMatchBackfillService) {
        this.riotApiClient = riotApiClient;
        this.crawlerSummonerService = crawlerSummonerService;
        this.crawlerMatchBackfillService = crawlerMatchBackfillService;
    }

    @Override
    public void run(String... args) {
        try {
            processApex("CHALLENGER", () -> riotApiClient.getChallengerLeague(SummonerService.SOLO_QUEUE));
            processApex("GRANDMASTER", () -> riotApiClient.getGrandmasterLeague(SummonerService.SOLO_QUEUE));
            processApex("MASTER", () -> riotApiClient.getMasterLeague(SummonerService.SOLO_QUEUE));
            for (String tier : DIVISION_TIERS) {
                for (String division : DIVISIONS) {
                    descendDivision(tier, division);
                }
            }
            log.info("Crawler finished - iron IV exhausted, process exiting");
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            // Same pattern as MatchCollectionQueue: a rejected key won't fix itself, so stop
            // the whole run immediately instead of burning hours of dead-key calls unnoticed.
            log.error("Riot API key rejected ({}) - aborting crawler run", e.getStatusCode());
        }
    }

    private void processApex(String label, Supplier<List<RiotLeagueSeedEntryResponse>> fetcher) {
        List<RiotLeagueSeedEntryResponse> entries;
        try {
            entries = fetcher.get();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch {} league", label, e);
            return;
        }
        processEntries(entries);
    }

    private void descendDivision(String tier, String division) {
        int page = 1;
        while (true) {
            List<RiotLeagueSeedEntryResponse> entries;
            try {
                entries = riotApiClient.getLeagueEntries(SummonerService.SOLO_QUEUE, tier, division, page);
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to fetch {} {} page {}", tier, division, page, e);
                return;
            }
            if (entries.isEmpty()) {
                return;
            }
            processEntries(entries);
            page++;
        }
    }

    private void processEntries(List<RiotLeagueSeedEntryResponse> entries) {
        for (RiotLeagueSeedEntryResponse entry : entries) {
            try {
                Summoner summoner = crawlerSummonerService.upsert(entry);
                crawlerMatchBackfillService.backfill(summoner);
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to process puuid {}", entry.puuid(), e);
            }
        }
    }
}
