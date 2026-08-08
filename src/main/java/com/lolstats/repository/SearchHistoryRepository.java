package com.lolstats.repository;

import com.lolstats.domain.SearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    List<SearchHistory> findByUserIdOrderBySearchedAtDesc(Long userId, Pageable pageable);

    // Dedup-on-search (SearchHistoryService) - re-searching the same summoner moves it to the
    // top instead of piling up duplicate rows, matching the client-side recentSearches
    // localStorage pattern already used in profile.html for anonymous users.
    Optional<SearchHistory> findByUserIdAndSummonerId(Long userId, Long summonerId);
}
