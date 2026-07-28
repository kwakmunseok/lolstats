package com.lolstats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "search_counts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchCount {

    @Id
    @Column(name = "summoner_id")
    private Long summonerId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "summoner_id")
    private Summoner summoner;

    @Column(name = "search_count", nullable = false)
    @Builder.Default
    private Long searchCount = 0L;

    @Column(name = "last_searched_at")
    private Instant lastSearchedAt;
}
