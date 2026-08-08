package com.lolstats.service;

import com.lolstats.domain.SearchHistory;
import com.lolstats.domain.Summoner;
import com.lolstats.domain.User;
import com.lolstats.repository.SearchHistoryRepository;
import com.lolstats.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchHistoryServiceTest {

    @Mock
    private SearchHistoryRepository searchHistoryRepository;

    @Mock
    private UserRepository userRepository;

    private SearchHistoryService service;

    @BeforeEach
    void setUp() {
        service = new SearchHistoryService(searchHistoryRepository, userRepository);
        lenient().when(searchHistoryRepository.save(any(SearchHistory.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Summoner summoner(Long id) {
        return Summoner.builder().id(id).gameName("Grizzly").tagLine("KR3").build();
    }

    @Test
    void record_newSearch_savesRow() {
        when(searchHistoryRepository.findByUserIdAndSummonerId(1L, 5L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().id(1L).build());

        service.record(1L, summoner(5L));

        ArgumentCaptor<SearchHistory> captor = ArgumentCaptor.forClass(SearchHistory.class);
        verify(searchHistoryRepository).save(captor.capture());
        assertEquals(5L, captor.getValue().getSummoner().getId());
    }

    @Test
    void record_sameSummonerAgain_updatesTimestampInsteadOfDuplicating() {
        Instant old = Instant.now().minusSeconds(3600);
        SearchHistory existing = SearchHistory.builder().id(9L).summoner(summoner(5L)).searchedAt(old).build();
        when(searchHistoryRepository.findByUserIdAndSummonerId(1L, 5L)).thenReturn(Optional.of(existing));

        service.record(1L, summoner(5L));

        ArgumentCaptor<SearchHistory> captor = ArgumentCaptor.forClass(SearchHistory.class);
        verify(searchHistoryRepository).save(captor.capture());
        assertEquals(9L, captor.getValue().getId());
        assertTrue(captor.getValue().getSearchedAt().isAfter(old));
        verify(userRepository, never()).getReferenceById(any());
    }

    @Test
    void list_delegatesToRepositoryMostRecentFirst() {
        SearchHistory h = SearchHistory.builder().id(1L).summoner(summoner(5L)).searchedAt(Instant.now()).build();
        when(searchHistoryRepository.findByUserIdOrderBySearchedAtDesc(any(), any())).thenReturn(List.of(h));

        List<SearchHistory> result = service.list(1L);

        assertEquals(List.of(h), result);
    }
}
