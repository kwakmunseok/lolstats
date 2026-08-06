package com.lolstats.repository;

import com.lolstats.domain.TierHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TierHistoryRepository extends JpaRepository<TierHistory, Long> {

    // Dedup check before inserting a new snapshot (SummonerService).
    Optional<TierHistory> findTopBySummonerIdOrderByRecordedAtDesc(Long summonerId);

    // Chart/table display (oldest first - Task 3).
    List<TierHistory> findBySummonerIdOrderByRecordedAtAsc(Long summonerId);
}
