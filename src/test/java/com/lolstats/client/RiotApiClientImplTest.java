package com.lolstats.client;

import com.lolstats.client.dto.RiotAccountResponse;
import com.lolstats.client.dto.RiotLeagueEntryResponse;
import com.lolstats.client.dto.RiotMatchResponse;
import com.lolstats.client.dto.RiotSummonerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// No real Riot API key needed: MockRestServiceServer intercepts the HTTP layer so this
// verifies routing (asia vs kr), headers, and response mapping in isolation.
class RiotApiClientImplTest {

    private static final String PLATFORM_URL = "https://kr.api.riotgames.com";
    private static final String REGIONAL_URL = "https://asia.api.riotgames.com";
    private static final String API_KEY = "test-key";

    private MockRestServiceServer platformServer;
    private MockRestServiceServer regionalServer;
    private RiotApiClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder platformBuilder = RestClient.builder()
                .baseUrl(PLATFORM_URL)
                .defaultHeader("X-Riot-Token", API_KEY);
        RestClient.Builder regionalBuilder = RestClient.builder()
                .baseUrl(REGIONAL_URL)
                .defaultHeader("X-Riot-Token", API_KEY);

        platformServer = MockRestServiceServer.bindTo(platformBuilder).build();
        regionalServer = MockRestServiceServer.bindTo(regionalBuilder).build();

        client = new RiotApiClientImpl(platformBuilder.build(), regionalBuilder.build());
    }

    @Test
    void getAccountByRiotId_usesRegionalRoutingAndKey() {
        regionalServer.expect(requestTo(REGIONAL_URL + "/riot/account/v1/accounts/by-riot-id/Hide%20on%20bush/KR1"))
                .andExpect(method(GET))
                .andExpect(header("X-Riot-Token", API_KEY))
                .andRespond(withSuccess("""
                        {"puuid":"puuid-1","gameName":"Hide on bush","tagLine":"KR1"}
                        """, MediaType.APPLICATION_JSON));

        RiotAccountResponse result = client.getAccountByRiotId("Hide on bush", "KR1");

        assertEquals("puuid-1", result.puuid());
        regionalServer.verify();
    }

    @Test
    void getSummonerByPuuid_usesPlatformRouting() {
        platformServer.expect(requestTo(PLATFORM_URL + "/lol/summoner/v4/summoners/by-puuid/puuid-1"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"puuid":"puuid-1","profileIconId":4568,"summonerLevel":612}
                        """, MediaType.APPLICATION_JSON));

        RiotSummonerResponse result = client.getSummonerByPuuid("puuid-1");

        assertEquals(4568, result.profileIconId());
        assertEquals(612, result.summonerLevel());
        platformServer.verify();
    }

    @Test
    void getLeagueEntriesByPuuid_usesPlatformRoutingAndByPuuidEndpoint() {
        platformServer.expect(requestTo(PLATFORM_URL + "/lol/league/v4/entries/by-puuid/puuid-1"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [{"queueType":"RANKED_SOLO_5x5","tier":"CHALLENGER","rank":"I","leaguePoints":1487,"wins":312,"losses":198}]
                        """, MediaType.APPLICATION_JSON));

        List<RiotLeagueEntryResponse> result = client.getLeagueEntriesByPuuid("puuid-1");

        assertEquals(1, result.size());
        assertEquals("RANKED_SOLO_5x5", result.get(0).queueType());
        platformServer.verify();
    }

    @Test
    void getMatchIdsByPuuid_usesRegionalRouting() {
        regionalServer.expect(requestTo(REGIONAL_URL + "/lol/match/v5/matches/by-puuid/puuid-1/ids?count=20"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        ["KR_1111111111","KR_2222222222"]
                        """, MediaType.APPLICATION_JSON));

        List<String> result = client.getMatchIdsByPuuid("puuid-1", 20);

        assertEquals(2, result.size());
        regionalServer.verify();
    }

    @Test
    void getMatchById_usesRegionalRoutingAndParsesNestedParticipants() {
        regionalServer.expect(requestTo(REGIONAL_URL + "/lol/match/v5/matches/KR_1111111111"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "metadata": {"matchId": "KR_1111111111"},
                          "info": {
                            "gameCreation": 1700000000000,
                            "gameDuration": 1800,
                            "queueId": 420,
                            "participants": [
                              {
                                "puuid": "puuid-1",
                                "riotIdGameName": "Faker",
                                "riotIdTagline": "KR1",
                                "championId": 103,
                                "teamPosition": "MIDDLE",
                                "kills": 10, "deaths": 2, "assists": 8,
                                "win": true,
                                "summoner1Id": 4, "summoner2Id": 12,
                                "item0": 1001, "item1": 0, "item2": 0, "item3": 0, "item4": 0, "item5": 0, "item6": 3340,
                                "perks": {"statPerks": {}, "styles": []}
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        RiotMatchResponse result = client.getMatchById("KR_1111111111");

        assertEquals("KR_1111111111", result.metadata().matchId());
        assertEquals(420, result.info().queueId());
        assertEquals(1, result.info().participants().size());
        assertEquals("Faker", result.info().participants().get(0).riotIdGameName());
        assertEquals(103, result.info().participants().get(0).championId());
        regionalServer.verify();
    }

    @Test
    void getMatchById_retriesAfter429_thenSucceeds() {
        regionalServer.expect(requestTo(REGIONAL_URL + "/lol/match/v5/matches/KR_9999999999"))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        regionalServer.expect(requestTo(REGIONAL_URL + "/lol/match/v5/matches/KR_9999999999"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "metadata": {"matchId": "KR_9999999999"},
                          "info": {
                            "gameCreation": 1700000000000,
                            "gameDuration": 1800,
                            "queueId": 420,
                            "participants": []
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        RiotMatchResponse result = client.getMatchById("KR_9999999999");

        assertEquals("KR_9999999999", result.metadata().matchId());
        regionalServer.verify();
    }

    @Test
    void getMatchById_doesNotRetryOn401() {
        regionalServer.expect(requestTo(REGIONAL_URL + "/lol/match/v5/matches/KR_9999999999"))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(HttpClientErrorException.Unauthorized.class, () -> client.getMatchById("KR_9999999999"));
        regionalServer.verify();
    }
}
