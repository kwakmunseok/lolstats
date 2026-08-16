package com.lolstats.service;

import com.lolstats.client.ddragon.DataDragonClient;
import com.lolstats.client.ddragon.dto.ChampionListResponse;
import com.lolstats.client.ddragon.dto.ItemListResponse;
import com.lolstats.client.ddragon.dto.RuneTreeResponse;
import com.lolstats.client.ddragon.dto.SummonerSpellListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataDragonServiceTest {

    @Mock
    private DataDragonClient client;

    private DataDragonService service;

    @BeforeEach
    void setUp() {
        service = new DataDragonService(client, 24);
        stubDdragonResponses("16.14.1");
    }

    private void stubDdragonResponses(String version) {
        when(client.getVersions()).thenReturn(List.of(version, "16.13.1"));
        when(client.getChampions(version)).thenReturn(new ChampionListResponse(Map.of(
                "Ahri", new ChampionListResponse.ChampionData("103", "아리",
                        new ChampionListResponse.ChampionImage("Ahri.png")))));
        when(client.getItems(version)).thenReturn(new ItemListResponse(Map.of(
                "1001", new ItemListResponse.ItemData("장화", "<mainText>이동속도 +25</mainText>",
                        new ItemListResponse.ItemImage("1001.png")))));
        when(client.getSummonerSpells(version)).thenReturn(new SummonerSpellListResponse(Map.of(
                "SummonerFlash", new SummonerSpellListResponse.SpellData("4", "점멸",
                        new SummonerSpellListResponse.SpellImage("SummonerFlash.png")))));
        when(client.getRuneTrees(version)).thenReturn(List.of(
                new RuneTreeResponse(8100, "perk-images/Styles/7200_Domination.png", "지배", List.of(
                        new RuneTreeResponse.RuneSlot(List.of(
                                new RuneTreeResponse.RuneData(8112,
                                        "perk-images/Styles/Domination/Electrocute/Electrocute.png", "감전")))))));
    }

    @Test
    void championLookup_returnsKoreanNameAndVersionedImageUrl() {
        Optional<DataDragonService.ChampionInfo> ahri = service.getChampion(103);

        assertTrue(ahri.isPresent());
        assertEquals("아리", ahri.get().name());
        assertEquals("https://ddragon.leagueoflegends.com/cdn/16.14.1/img/champion/Ahri.png", ahri.get().imageUrl());
    }

    @Test
    void itemLookup_worksByRawItemId() {
        Optional<DataDragonService.ItemInfo> boots = service.getItem(1001);

        assertTrue(boots.isPresent());
        assertEquals("장화", boots.get().name());
        assertEquals("https://ddragon.leagueoflegends.com/cdn/16.14.1/img/item/1001.png", boots.get().imageUrl());
        assertEquals("<mainText>이동속도 +25</mainText>", boots.get().description());
    }

    @Test
    void spellLookup_resolvesNumericSpellIdToName() {
        Optional<DataDragonService.SpellInfo> flash = service.getSpell(4);

        assertTrue(flash.isPresent());
        assertEquals("점멸", flash.get().name());
    }

    @Test
    void runeIconLookup_coversBothTreeAndIndividualRuneIds() {
        assertEquals(Optional.of("https://ddragon.leagueoflegends.com/cdn/img/perk-images/Styles/7200_Domination.png"),
                service.getRuneIconUrl(8100));
        assertEquals(Optional.of("https://ddragon.leagueoflegends.com/cdn/img/perk-images/Styles/Domination/Electrocute/Electrocute.png"),
                service.getRuneIconUrl(8112));
    }

    @Test
    void unknownId_returnsEmptyRatherThanThrowing() {
        assertEquals(Optional.empty(), service.getChampion(999999));
        assertEquals(Optional.empty(), service.getItem(0));
    }

    @Test
    void profileIconUrl_usesVersionedCdnPath() {
        assertEquals("https://ddragon.leagueoflegends.com/cdn/16.14.1/img/profileicon/6.png",
                service.getProfileIconUrl(6));
    }

    @Test
    void secondLookupWithinTtl_doesNotRefetch() {
        service.getChampion(103);
        service.getChampion(103);
        service.getItem(1001);

        verify(client, times(1)).getVersions();
        verify(client, times(1)).getChampions("16.14.1");
    }

    @Test
    void lookupAfterIntervalElapses_refetchesNewVersion() {
        // 0-hour interval means isFresh() is false as soon as any time passes since the last
        // refresh - a deterministic way to force the "stale" branch without sleeping or a Clock.
        DataDragonService alwaysStale = new DataDragonService(client, 0);
        alwaysStale.getChampion(103); // @BeforeEach already stubbed client for version 16.14.1

        stubDdragonResponses("16.15.1");
        Optional<DataDragonService.ChampionInfo> ahri = alwaysStale.getChampion(103);

        assertTrue(ahri.isPresent());
        assertEquals("https://ddragon.leagueoflegends.com/cdn/16.15.1/img/champion/Ahri.png", ahri.get().imageUrl());
        verify(client, times(2)).getVersions();
    }
}
