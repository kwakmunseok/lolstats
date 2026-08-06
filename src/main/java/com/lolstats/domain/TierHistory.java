package com.lolstats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// One row per tier/rank/LP change (PROJECT_PLAN.md §6) - not one row per search. Written only
// when it differs from the summoner's latest snapshot (SummonerService), so a frequently
// searched summoner doesn't flood this table.
@Entity
@Table(name = "tier_history", indexes = @Index(name = "idx_tier_history_summoner_id", columnList = "summoner_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TierHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summoner_id", nullable = false)
    private Summoner summoner;

    private String tier;

    @Column(name = "`rank`")
    private String rank;

    @Column(name = "league_points")
    private Integer leaguePoints;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
