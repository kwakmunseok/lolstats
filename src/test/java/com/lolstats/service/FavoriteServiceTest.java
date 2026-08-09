package com.lolstats.service;

import com.lolstats.domain.Favorite;
import com.lolstats.domain.Summoner;
import com.lolstats.domain.User;
import com.lolstats.repository.FavoriteRepository;
import com.lolstats.repository.SummonerRepository;
import com.lolstats.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private SummonerRepository summonerRepository;

    @Mock
    private UserRepository userRepository;

    private FavoriteService service;

    @BeforeEach
    void setUp() {
        service = new FavoriteService(favoriteRepository, summonerRepository, userRepository);
        lenient().when(favoriteRepository.save(any(Favorite.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Summoner summoner(Long id) {
        return Summoner.builder().id(id).gameName("Grizzly").tagLine("KR3").build();
    }

    @Test
    void add_newFavorite_savesIt() {
        when(summonerRepository.findById(5L)).thenReturn(Optional.of(summoner(5L)));
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().id(1L).build());
        when(favoriteRepository.findByUserIdAndSummonerId(1L, 5L)).thenReturn(Optional.empty());

        service.add(1L, 5L);

        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteRepository).save(captor.capture());
        assertEquals(5L, captor.getValue().getSummoner().getId());
    }

    @Test
    void add_alreadyFavorited_doesNotDuplicate() {
        when(summonerRepository.findById(5L)).thenReturn(Optional.of(summoner(5L)));
        Favorite existing = Favorite.builder().id(9L).summoner(summoner(5L)).createdAt(Instant.now()).build();
        when(favoriteRepository.findByUserIdAndSummonerId(1L, 5L)).thenReturn(Optional.of(existing));

        service.add(1L, 5L);

        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void add_unknownSummoner_throwsNotFound() {
        when(summonerRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.add(1L, 999L));

        assertEquals(404, ex.getStatusCode().value());
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void remove_existing_deletesIt() {
        Favorite existing = Favorite.builder().id(9L).summoner(summoner(5L)).createdAt(Instant.now()).build();
        when(favoriteRepository.findByUserIdAndSummonerId(1L, 5L)).thenReturn(Optional.of(existing));

        service.remove(1L, 5L);

        verify(favoriteRepository).delete(existing);
    }

    @Test
    void remove_notFavorited_doesNothing() {
        when(favoriteRepository.findByUserIdAndSummonerId(1L, 5L)).thenReturn(Optional.empty());

        service.remove(1L, 5L);

        verify(favoriteRepository, never()).delete(any());
    }

    @Test
    void isFavorited_existing_returnsTrue() {
        when(favoriteRepository.findByUserIdAndSummonerId(1L, 5L))
                .thenReturn(Optional.of(Favorite.builder().id(9L).build()));

        assertTrue(service.isFavorited(1L, 5L));
    }

    @Test
    void isFavorited_notFavorited_returnsFalse() {
        when(favoriteRepository.findByUserIdAndSummonerId(1L, 5L)).thenReturn(Optional.empty());

        assertFalse(service.isFavorited(1L, 5L));
    }

    @Test
    void list_returnsFavoritesOrderedByMostRecentFirst() {
        Favorite older = Favorite.builder().id(1L).summoner(summoner(5L)).createdAt(Instant.now().minusSeconds(60)).build();
        Favorite newer = Favorite.builder().id(2L).summoner(summoner(6L)).createdAt(Instant.now()).build();
        when(favoriteRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(newer, older));

        List<Favorite> result = service.list(1L);

        assertEquals(List.of(newer, older), result);
    }
}
