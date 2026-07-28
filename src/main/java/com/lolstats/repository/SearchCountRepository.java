package com.lolstats.repository;

import com.lolstats.domain.SearchCount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchCountRepository extends JpaRepository<SearchCount, Long> {
}
