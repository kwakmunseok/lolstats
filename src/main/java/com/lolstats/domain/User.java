package com.lolstats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String nickname;

    // Email verification is deferred (PHASE5_PLAN.md §1) - accounts are active on signup, so
    // this stays true from creation. Column kept per PROJECT_PLAN.md §6 schema for when the
    // verification flow returns.
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified;

    // Login lockout is deferred alongside email verification (PHASE5_PLAN.md §1) - unused until
    // that flow is built, kept here so the schema doesn't need a later migration to add them.
    @Column(name = "login_fail_count", nullable = false)
    private Integer loginFailCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
