package com.lolstats.service;

import com.lolstats.domain.SearchHistory;
import com.lolstats.domain.Summoner;
import com.lolstats.repository.SearchHistoryRepository;
import com.lolstats.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class SearchHistoryService {

    private static final int RECENT_LIMIT = 20;

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    public SearchHistoryService(SearchHistoryRepository searchHistoryRepository, UserRepository userRepository) {
        this.searchHistoryRepository = searchHistoryRepository;
        this.userRepository = userRepository;
    }

    // Dedup-on-search: re-searching the same summoner moves it to the top of the list rather
    // than piling up duplicate rows (same UX as the client-side recentSearches localStorage
    // list already used for anonymous users - profile.html).
    public void record(Long userId, Summoner summoner) {
        SearchHistory entry = searchHistoryRepository.findByUserIdAndSummonerId(userId, summoner.getId())
                .orElseGet(() -> SearchHistory.builder()
                        .user(userRepository.getReferenceById(userId))
                        .summoner(summoner)
                        .build());
        entry.setSearchedAt(Instant.now());
        searchHistoryRepository.save(entry);
    }

    public List<SearchHistory> list(Long userId) {
        return searchHistoryRepository.findByUserIdOrderBySearchedAtDesc(userId, PageRequest.of(0, RECENT_LIMIT));
    }
}
