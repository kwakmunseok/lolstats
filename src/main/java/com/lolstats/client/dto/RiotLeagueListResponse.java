package com.lolstats.client.dto;

import java.util.List;

// league-v4 apex endpoints (challengerleagues/grandmasterleagues/masterleagues by-queue).
// Unlike entries/{queue}/{tier}/{division}, tier is on the wrapper, not each entry.
public record RiotLeagueListResponse(String tier, List<RiotLeagueItemResponse> entries) {
}
