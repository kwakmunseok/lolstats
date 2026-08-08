package com.lolstats.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email String email,
        // bcrypt silently truncates input over 72 bytes - capped here so a long password
        // doesn't create a false sense of extra entropy.
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 2, max = 20) String nickname,
        @AssertTrue(message = "약관에 동의해야 합니다") boolean agreedToTerms) {
}
