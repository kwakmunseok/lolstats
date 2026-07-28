package com.lolstats.repository;

import com.lolstats.domain.MatchParticipant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    List<MatchParticipant> findByPuuid(String puuid);

    Page<MatchParticipant> findByPuuid(String puuid, Pageable pageable);

    List<MatchParticipant> findByMatchId(Long matchId);
}
