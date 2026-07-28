package com.lolstats.repository;

import com.lolstats.domain.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    Optional<Match> findByRiotMatchId(String riotMatchId);

    boolean existsByRiotMatchId(String riotMatchId);
}
