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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// No FK to Summoner by design: most of a match's 10 participants were never
// searched, so participants keep a puuid + name snapshot instead (see PROJECT_PLAN.md §6).
@Entity
@Table(name = "match_participants", indexes = @Index(name = "idx_match_participants_puuid", columnList = "puuid"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(nullable = false)
    private String puuid;

    @Column(name = "game_name")
    private String gameName;

    @Column(name = "tag_line")
    private String tagLine;

    @Column(name = "champion_id")
    private Integer championId;

    @Column(name = "team_position")
    private String teamPosition;

    private Integer kills;

    private Integer deaths;

    private Integer assists;

    private Boolean win;

    @Column(name = "spell1_id")
    private Integer spell1Id;

    @Column(name = "spell2_id")
    private Integer spell2Id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items_json", columnDefinition = "json")
    private String itemsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "runes_json", columnDefinition = "json")
    private String runesJson;
}
