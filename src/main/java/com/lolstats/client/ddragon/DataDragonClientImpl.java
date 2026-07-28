package com.lolstats.client.ddragon;

import com.lolstats.client.ddragon.dto.ChampionListResponse;
import com.lolstats.client.ddragon.dto.ItemListResponse;
import com.lolstats.client.ddragon.dto.RuneTreeResponse;
import com.lolstats.client.ddragon.dto.SummonerSpellListResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class DataDragonClientImpl implements DataDragonClient {

    private final RestClient restClient;

    public DataDragonClientImpl(@Value("${ddragon.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public List<String> getVersions() {
        return restClient.get()
                .uri("/api/versions.json")
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {
                });
    }

    @Override
    public ChampionListResponse getChampions(String version) {
        return restClient.get()
                .uri("/cdn/{version}/data/ko_KR/champion.json", version)
                .retrieve()
                .body(ChampionListResponse.class);
    }

    @Override
    public ItemListResponse getItems(String version) {
        return restClient.get()
                .uri("/cdn/{version}/data/ko_KR/item.json", version)
                .retrieve()
                .body(ItemListResponse.class);
    }

    @Override
    public SummonerSpellListResponse getSummonerSpells(String version) {
        return restClient.get()
                .uri("/cdn/{version}/data/ko_KR/summoner.json", version)
                .retrieve()
                .body(SummonerSpellListResponse.class);
    }

    @Override
    public List<RuneTreeResponse> getRuneTrees(String version) {
        return restClient.get()
                .uri("/cdn/{version}/data/ko_KR/runesReforged.json", version)
                .retrieve()
                .body(new ParameterizedTypeReference<List<RuneTreeResponse>>() {
                });
    }
}
