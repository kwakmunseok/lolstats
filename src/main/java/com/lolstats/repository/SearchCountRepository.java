package com.lolstats.repository;

import com.lolstats.domain.SearchCount;
import com.lolstats.domain.Summoner;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SearchCountRepository extends JpaRepository<SearchCount, Long> {

    // Only ever-searched summoners are candidates (autocomplete's own documented limit -
    // PROJECT_PLAN.md §4 설계 노트), so driving the query from SearchCount is safe.
    @Query("SELECT sc.summoner FROM SearchCount sc "
            + "WHERE LOWER(sc.summoner.gameName) LIKE LOWER(CONCAT(:prefix, '%')) "
            + "ORDER BY sc.lastSearchedAt DESC")
    List<Summoner> findSummonersByGameNamePrefix(@Param("prefix") String prefix, Pageable pageable);

    @Query("SELECT sc.summoner FROM SearchCount sc ORDER BY sc.searchCount DESC, sc.lastSearchedAt DESC")
    List<Summoner> findPopularSummoners(Pageable pageable);
}
