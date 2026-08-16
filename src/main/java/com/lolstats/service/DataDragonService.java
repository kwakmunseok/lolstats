package com.lolstats.service;

import com.lolstats.client.ddragon.DataDragonClient;
import com.lolstats.client.ddragon.dto.ChampionListResponse;
import com.lolstats.client.ddragon.dto.ItemListResponse;
import com.lolstats.client.ddragon.dto.RuneTreeResponse;
import com.lolstats.client.ddragon.dto.SummonerSpellListResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// Caches Data Dragon's champion/item/spell/rune data in memory, refreshed at most once per
// `refresh-interval-hours` (default 24h - patches don't land more often than that). Never
// hardcodes a version: every lookup goes through the currently cached version string, so a
// stale cache just means yesterday's (still-valid) icons, not broken ones.
@Service
public class DataDragonService {

    private static final String CDN_BASE = "https://ddragon.leagueoflegends.com/cdn/";
    private static final String RUNE_ICON_BASE = "https://ddragon.leagueoflegends.com/cdn/img/";

    private final DataDragonClient client;
    private final Duration refreshInterval;

    private volatile String currentVersion;
    private volatile Map<Integer, ChampionInfo> championsById = Map.of();
    private volatile Map<Integer, ItemInfo> itemsById = Map.of();
    private volatile Map<Integer, SpellInfo> spellsById = Map.of();
    private volatile Map<Integer, String> runeIconById = Map.of();
    private volatile Instant lastRefreshedAt;

    public DataDragonService(
            DataDragonClient client,
            @Value("${ddragon.refresh-interval-hours}") long refreshIntervalHours) {
        this.client = client;
        this.refreshInterval = Duration.ofHours(refreshIntervalHours);
    }

    @PostConstruct
    void warmUp() {
        refreshIfStale();
    }

    public String getCurrentVersion() {
        refreshIfStale();
        return currentVersion;
    }

    public Optional<ChampionInfo> getChampion(int championId) {
        refreshIfStale();
        return Optional.ofNullable(championsById.get(championId));
    }

    public Optional<ItemInfo> getItem(int itemId) {
        refreshIfStale();
        return Optional.ofNullable(itemsById.get(itemId));
    }

    public Optional<SpellInfo> getSpell(int spellId) {
        refreshIfStale();
        return Optional.ofNullable(spellsById.get(spellId));
    }

    public Optional<String> getRuneIconUrl(int runeOrStyleId) {
        refreshIfStale();
        return Optional.ofNullable(runeIconById.get(runeOrStyleId));
    }

    public String getProfileIconUrl(int profileIconId) {
        refreshIfStale();
        return CDN_BASE + currentVersion + "/img/profileicon/" + profileIconId + ".png";
    }

    private void refreshIfStale() {
        if (isFresh()) {
            return;
        }
        refresh();
    }

    private boolean isFresh() {
        return lastRefreshedAt != null && lastRefreshedAt.isAfter(Instant.now().minus(refreshInterval));
    }

    private synchronized void refresh() {
        if (isFresh()) {
            return; // another thread refreshed while this one was waiting for the lock
        }

        String version = client.getVersions().get(0);
        ChampionListResponse champions = client.getChampions(version);
        ItemListResponse items = client.getItems(version);
        SummonerSpellListResponse spells = client.getSummonerSpells(version);
        List<RuneTreeResponse> runeTrees = client.getRuneTrees(version);

        this.championsById = champions.data().values().stream()
                .collect(Collectors.toMap(
                        c -> Integer.parseInt(c.key()),
                        c -> new ChampionInfo(Integer.parseInt(c.key()), c.name(),
                                CDN_BASE + version + "/img/champion/" + c.image().full())));

        this.itemsById = items.data().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> Integer.parseInt(e.getKey()),
                        e -> new ItemInfo(Integer.parseInt(e.getKey()), e.getValue().name(),
                                CDN_BASE + version + "/img/item/" + e.getValue().image().full(),
                                e.getValue().description())));

        this.spellsById = spells.data().values().stream()
                .collect(Collectors.toMap(
                        s -> Integer.parseInt(s.key()),
                        s -> new SpellInfo(Integer.parseInt(s.key()), s.name(),
                                CDN_BASE + version + "/img/spell/" + s.image().full())));

        Map<Integer, String> newRuneIcons = new HashMap<>();
        for (RuneTreeResponse tree : runeTrees) {
            newRuneIcons.put(tree.id(), RUNE_ICON_BASE + tree.icon());
            for (RuneTreeResponse.RuneSlot slot : tree.slots()) {
                for (RuneTreeResponse.RuneData rune : slot.runes()) {
                    newRuneIcons.put(rune.id(), RUNE_ICON_BASE + rune.icon());
                }
            }
        }
        this.runeIconById = Map.copyOf(newRuneIcons);

        this.currentVersion = version;
        this.lastRefreshedAt = Instant.now();
    }

    public record ChampionInfo(int id, String name, String imageUrl) {
    }

    public record ItemInfo(int id, String name, String imageUrl, String description) {
    }

    public record SpellInfo(int id, String name, String imageUrl) {
    }
}
