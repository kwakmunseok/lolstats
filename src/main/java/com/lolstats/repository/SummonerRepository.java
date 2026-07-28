package com.lolstats.repository;

import com.lolstats.domain.Summoner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SummonerRepository extends JpaRepository<Summoner, Long> {

    Optional<Summoner> findByPuuid(String puuid);

    // (game_name, tag_line) has no unique constraint - a Riot ID can change hands
    // (PROJECT_PLAN.md §6), so more than one row can match.
    List<Summoner> findByGameNameAndTagLine(String gameName, String tagLine);
}
