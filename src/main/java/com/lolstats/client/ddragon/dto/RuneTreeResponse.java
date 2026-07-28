package com.lolstats.client.ddragon.dto;

import java.util.List;

// runesReforged.json: a top-level array of 5 trees. Both tree ids (e.g. 8100 "Domination")
// and individual rune ids (e.g. 8112 "Electrocute") appear in match perks and need icons -
// they don't share the id space, so both can flatten into one id->icon map.
public record RuneTreeResponse(int id, String icon, String name, List<RuneSlot> slots) {

    public record RuneSlot(List<RuneData> runes) {
    }

    public record RuneData(int id, String icon, String name) {
    }
}
