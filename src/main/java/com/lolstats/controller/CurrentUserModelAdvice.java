package com.lolstats.controller;

import com.lolstats.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Scoped to PageController only (not the @RestController API controllers - the "currentUser"
// model attribute is meaningless for JSON responses and would cost an extra query per API call
// for nothing). Runs before every PageController method, so the nav fragment can show
// login/logout state on every screen without each controller method wiring it manually.
@ControllerAdvice(assignableTypes = PageController.class)
public class CurrentUserModelAdvice {

    private final UserRepository userRepository;

    public CurrentUserModelAdvice(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ModelAttribute("currentUser")
    public CurrentUserView currentUser(@AuthenticationPrincipal Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(u -> new CurrentUserView(u.getId(), u.getNickname()))
                .orElse(null);
    }

    public record CurrentUserView(Long id, String nickname) {
    }
}
