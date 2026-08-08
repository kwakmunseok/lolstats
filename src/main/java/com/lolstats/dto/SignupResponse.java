package com.lolstats.dto;

import com.lolstats.domain.User;

public record SignupResponse(Long id, String email, String nickname) {

    public static SignupResponse from(User u) {
        return new SignupResponse(u.getId(), u.getEmail(), u.getNickname());
    }
}
