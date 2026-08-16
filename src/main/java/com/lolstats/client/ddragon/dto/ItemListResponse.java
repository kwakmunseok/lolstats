package com.lolstats.client.ddragon.dto;

import java.util.Map;

// item.json: data is keyed directly by itemId (as a string) - no separate "key" field needed.
public record ItemListResponse(Map<String, ItemData> data) {

    public record ItemData(String name, String description, ItemImage image) {
    }

    public record ItemImage(String full) {
    }
}
