package com.lolstats.repository;

import com.lolstats.domain.MatchParticipant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    List<MatchParticipant> findByPuuid(String puuid);

    Page<MatchParticipant> findByPuuid(String puuid, Pageable pageable);

    // Match lists (profile screen, /api/summoners/{id}/matches) are Summoner's Rift only
    // (PROJECT_PLAN.md §4 MVP scope) - Arena/ARAM/etc. are still collected and stored
    // (principle ①: never re-request an already-seen match id), just not surfaced here.
    Page<MatchParticipant> findByPuuidAndMatch_QueueTypeIn(String puuid, Collection<String> queueTypes, Pageable pageable);

    // OrderByIdAsc matters: MatchParticipant uses GenerationType.IDENTITY so saveAll() assigns
    // ids in insertion order, which is the order MatchService iterated Riot's participant array
    // in - i.e. id-ascending reconstructs that array order, which PageController/MatchController
    // rely on for participantId = (list index) + 1 (Timeline API's participantId).
    List<MatchParticipant> findByMatchIdOrderByIdAsc(Long matchId);

    // Stats aggregation (Phase 3) - same Rift-only queue filter as the match list above, but
    // unpaged: a live user's own collection caps at MatchService.MATCH_ID_FETCH_COUNT (20),
    // and even a heavily-crawled puuid picking up extra rows as a teammate/opponent in other
    // summoners' backfills (verified live: 400+ rows for a popular apex player) is still a
    // trivial in-memory aggregation size - no batch table needed either way.
    List<MatchParticipant> findByPuuidAndMatch_QueueTypeInOrderByMatch_GameCreationDesc(
            String puuid, Collection<String> queueTypes);
}
