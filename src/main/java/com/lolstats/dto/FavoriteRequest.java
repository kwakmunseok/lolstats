package com.lolstats.dto;

import jakarta.validation.constraints.NotNull;

public record FavoriteRequest(@NotNull Long summonerId) {
}
