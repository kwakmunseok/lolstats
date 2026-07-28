package com.lolstats.repository;

import com.lolstats.domain.Match;
import com.lolstats.domain.MatchParticipant;
import com.lolstats.domain.SearchCount;
import com.lolstats.domain.Summoner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

// Replace.NONE: uses the real MySQL datasource (dev profile), not an embedded DB —
// H2 is intentionally not used anywhere in this project (see PROJECT_PLAN.md §9.4).
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class RepositoryIntegrationTest {

    @Autowired
    private SummonerRepository summonerRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository matchParticipantRepository;

    @Autowired
    private SearchCountRepository searchCountRepository;

    @Test
    void summonerCrud() {
        Summoner saved = summonerRepository.save(Summoner.builder()
                .puuid("test-puuid-summoner")
                .gameName("Hide on bush")
                .tagLine("KR1")
                .profileIconId(4568)
                .summonerLevel(612)
                .tier("CHALLENGER")
                .rank("I")
                .leaguePoints(1487)
                .wins(312)
                .losses(198)
                .updatedAt(Instant.now())
                .build());

        Optional<Summoner> found = summonerRepository.findByPuuid("test-puuid-summoner");

        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertEquals("CHALLENGER", found.get().getTier());
    }

    @Test
    void matchCrud() {
        Match saved = matchRepository.save(Match.builder()
                .riotMatchId("KR_test-match-1")
                .gameCreation(Instant.now())
                .gameDuration(1800)
                .queueType("RANKED_SOLO_5x5")
                .build());

        Optional<Match> found = matchRepository.findByRiotMatchId("KR_test-match-1");

        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
    }

    @Test
    void matchParticipantCrud() {
        Match match = matchRepository.save(Match.builder()
                .riotMatchId("KR_test-match-2")
                .gameCreation(Instant.now())
                .gameDuration(1500)
                .queueType("RANKED_SOLO_5x5")
                .build());

        matchParticipantRepository.save(MatchParticipant.builder()
                .match(match)
                .puuid("test-puuid-participant")
                .gameName("Faker")
                .tagLine("KR1")
                .championId(103)
                .teamPosition("MIDDLE")
                .kills(10)
                .deaths(2)
                .assists(8)
                .win(true)
                .spell1Id(4)
                .spell2Id(12)
                .itemsJson("[1001,1002]")
                .runesJson("{\"primary\":8100}")
                .build());

        List<MatchParticipant> found = matchParticipantRepository.findByPuuid("test-puuid-participant");

        assertEquals(1, found.size());
        assertEquals(match.getId(), found.get(0).getMatch().getId());
    }

    @Test
    void searchCountCrud() {
        Summoner summoner = summonerRepository.save(Summoner.builder()
                .puuid("test-puuid-searchcount")
                .gameName("SearchTarget")
                .tagLine("KR1")
                .updatedAt(Instant.now())
                .build());

        searchCountRepository.save(SearchCount.builder()
                .summoner(summoner)
                .searchCount(1L)
                .lastSearchedAt(Instant.now())
                .build());

        Optional<SearchCount> found = searchCountRepository.findById(summoner.getId());

        assertTrue(found.isPresent());
        assertEquals(1L, found.get().getSearchCount());
    }
}
