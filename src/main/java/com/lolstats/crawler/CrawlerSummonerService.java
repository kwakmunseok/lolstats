package com.lolstats.crawler;

import com.lolstats.client.RiotApiClient;
import com.lolstats.client.dto.RiotAccountResponse;
import com.lolstats.client.dto.RiotLeagueSeedEntryResponse;
import com.lolstats.client.dto.RiotSummonerResponse;
import com.lolstats.domain.Summoner;
import com.lolstats.repository.SummonerRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

// Crawler-only upsert path (CRAWLER_PLAN.md §0/§3 Task 1) - deliberately not
// SummonerService.fetchAndUpsert(): that method is private and always makes 3 Riot calls,
// which doesn't fit the crawler's "new puuid: 2 calls, known puuid: 0 calls" split (the
// league-v4 listing that produced this entry already carries tier/rank/lp/wins/losses for
// free). Also never touches SEARCH_COUNTS/Redis - it doesn't call SummonerService at all.
@Service
public class CrawlerSummonerService {

    private final SummonerRepository summonerRepository;
    private final RiotApiClient riotApiClient;

    public CrawlerSummonerService(SummonerRepository summonerRepository, RiotApiClient riotApiClient) {
        this.summonerRepository = summonerRepository;
        this.riotApiClient = riotApiClient;
    }

    public Summoner upsert(RiotLeagueSeedEntryResponse entry) {
        Optional<Summoner> existing = summonerRepository.findByPuuid(entry.puuid());
        if (existing.isPresent()) {
            // Known puuid: league fields only, zero Riot calls. updatedAt is deliberately left
            // alone - name/icon/level weren't refreshed, so stamping it would let isFresh()
            // wrongly treat those stale fields as current and skip a real user's next refresh.
            Summoner summoner = existing.get();
            applyLeagueFields(summoner, entry);
            return summonerRepository.save(summoner);
        }

        RiotAccountResponse account = riotApiClient.getAccountByPuuid(entry.puuid());
        RiotSummonerResponse summonerInfo = riotApiClient.getSummonerByPuuid(entry.puuid());

        Summoner summoner = new Summoner();
        summoner.setPuuid(entry.puuid());
        summoner.setGameName(account.gameName());
        summoner.setTagLine(account.tagLine());
        summoner.setProfileIconId(summonerInfo.profileIconId());
        summoner.setSummonerLevel(summonerInfo.summonerLevel());
        applyLeagueFields(summoner, entry);
        summoner.setUpdatedAt(Instant.now());
        return summonerRepository.save(summoner);
    }

    private void applyLeagueFields(Summoner summoner, RiotLeagueSeedEntryResponse entry) {
        summoner.setTier(entry.tier());
        summoner.setRank(entry.rank());
        summoner.setLeaguePoints(entry.leaguePoints());
        summoner.setWins(entry.wins());
        summoner.setLosses(entry.losses());
    }
}
