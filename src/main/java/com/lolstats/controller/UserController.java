package com.lolstats.controller;

import com.lolstats.dto.FavoriteRequest;
import com.lolstats.dto.FavoriteResponse;
import com.lolstats.service.FavoriteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// /api/users/me/** - the auth boundary this controller lives behind is enforced entirely in
// SecurityConfig's authorizeHttpRequests matcher, not here.
@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final FavoriteService favoriteService;

    public UserController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping("/favorites")
    public List<FavoriteResponse> favorites(@AuthenticationPrincipal Long userId) {
        return favoriteService.list(userId).stream().map(FavoriteResponse::from).toList();
    }

    @PostMapping("/favorites")
    @ResponseStatus(HttpStatus.CREATED)
    public void addFavorite(@AuthenticationPrincipal Long userId, @Valid @RequestBody FavoriteRequest request) {
        favoriteService.add(userId, request.summonerId());
    }

    @DeleteMapping("/favorites/{summonerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@AuthenticationPrincipal Long userId, @PathVariable Long summonerId) {
        favoriteService.remove(userId, summonerId);
    }
}
