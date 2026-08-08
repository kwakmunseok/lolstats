package com.lolstats.service;

import com.lolstats.domain.Favorite;
import com.lolstats.domain.Summoner;
import com.lolstats.repository.FavoriteRepository;
import com.lolstats.repository.SummonerRepository;
import com.lolstats.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final SummonerRepository summonerRepository;
    private final UserRepository userRepository;

    public FavoriteService(
            FavoriteRepository favoriteRepository,
            SummonerRepository summonerRepository,
            UserRepository userRepository) {
        this.favoriteRepository = favoriteRepository;
        this.summonerRepository = summonerRepository;
        this.userRepository = userRepository;
    }

    public List<Favorite> list(Long userId) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // Idempotent - a favorite toggle button in the UI shouldn't need to special-case "already
    // favorited" as an error.
    public void add(Long userId, Long summonerId) {
        Summoner summoner = summonerRepository.findById(summonerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "summoner not found: " + summonerId));

        if (favoriteRepository.findByUserIdAndSummonerId(userId, summonerId).isPresent()) {
            return;
        }

        favoriteRepository.save(Favorite.builder()
                .user(userRepository.getReferenceById(userId))
                .summoner(summoner)
                .createdAt(Instant.now())
                .build());
    }

    // Idempotent - DELETE on an already-removed favorite is a no-op, not an error.
    public void remove(Long userId, Long summonerId) {
        favoriteRepository.findByUserIdAndSummonerId(userId, summonerId)
                .ifPresent(favoriteRepository::delete);
    }
}
