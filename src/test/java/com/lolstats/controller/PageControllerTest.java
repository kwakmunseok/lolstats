package com.lolstats.controller;

import com.lolstats.domain.MatchParticipant;
import com.lolstats.dto.ItemEvent;
import com.lolstats.repository.MatchParticipantRepository;
import com.lolstats.repository.MatchRepository;
import com.lolstats.service.ChampionStatsService;
import com.lolstats.service.DataDragonService;
import com.lolstats.service.FavoriteService;
import com.lolstats.service.MatchCollectionQueue;
import com.lolstats.service.MatchService;
import com.lolstats.service.SearchHistoryService;
import com.lolstats.service.SummonerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PageControllerTest {

    @Mock
    private SummonerService summonerService;
    @Mock
    private MatchService matchService;
    @Mock
    private MatchCollectionQueue matchCollectionQueue;
    @Mock
    private ChampionStatsService championStatsService;
    @Mock
    private DataDragonService dataDragonService;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchParticipantRepository matchParticipantRepository;
    @Mock
    private FavoriteService favoriteService;
    @Mock
    private SearchHistoryService searchHistoryService;

    private PageController controller;

    @BeforeEach
    void setUp() {
        controller = new PageController(
                summonerService, matchService, matchCollectionQueue, championStatsService, dataDragonService,
                matchRepository, matchParticipantRepository, JsonMapper.builder().build(),
                favoriteService, searchHistoryService);
    }

    private static MatchParticipant participant(String puuid) {
        return MatchParticipant.builder()
                .puuid(puuid).gameName(puuid).tagLine("KR1")
                .championId(0).spell1Id(0).spell2Id(0)
                .kills(0).deaths(0).assists(0).win(true)
                .itemsJson("[0,0,0,0,0,0,0]").runesJson("{}")
                .build();
    }

    // Regression guard for the single highest-risk piece of logic in the item-build-order
    // feature: participantId is derived as (list index + 1), and Timeline events are only
    // attached to the participant whose puuid matches focusPuuid. If either the +1 offset or
    // the id-ascending ordering assumption ever silently breaks, this test fails.
    @Test
    void toParticipantViews_attachesItemEventsOnlyToFocusedParticipantAtCorrectIndex() {
        lenient().when(dataDragonService.getItem(1055))
                .thenReturn(Optional.of(new DataDragonService.ItemInfo(1055, "Correct", "url-correct", "desc")));
        lenient().when(dataDragonService.getItem(3020))
                .thenReturn(Optional.of(new DataDragonService.ItemInfo(3020, "Wrong", "url-wrong", "desc")));

        List<MatchParticipant> participants = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> participant("puuid-" + i))
                .toList();
        String focusPuuid = "puuid-4"; // list index 4 -> correct participantId is 5

        List<ItemEvent> itemEvents = List.of(
                // Correct: matches (index 4) + 1 = 5.
                new ItemEvent(5, 1055, "ITEM_PURCHASED", 65000),
                // Decoy: matches the 0-based index (4) instead of the 1-based participantId -
                // would be picked up if the +1 offset were ever dropped.
                new ItemEvent(4, 3020, "ITEM_SOLD", 90000));

        List<PageController.ParticipantView> views =
                controller.toParticipantViews(participants, focusPuuid, itemEvents);

        for (int i = 0; i < views.size(); i++) {
            if (i == 4) {
                assertEquals(1, views.get(i).itemEvents().size());
                assertEquals("Correct", views.get(i).itemEvents().get(0).name());
            } else {
                assertTrue(views.get(i).itemEvents().isEmpty());
            }
        }
    }
}
