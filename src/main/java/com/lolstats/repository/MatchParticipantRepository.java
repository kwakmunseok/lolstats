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

    List<MatchParticipant> findByMatchId(Long matchId);
}
