package com.lolstats.repository;

import com.lolstats.domain.Summoner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SummonerRepository extends JpaRepository<Summoner, Long> {

    Optional<Summoner> findByPuuid(String puuid);
}
