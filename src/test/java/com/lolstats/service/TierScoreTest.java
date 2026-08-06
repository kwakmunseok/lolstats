package com.lolstats.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TierScoreTest {

    @Test
    void higherDivisionAlwaysBeatsLowerDivisionRegardlessOfLp() {
        int goldTwoAtNearPromotion = TierScore.score("GOLD", "II", 99);
        int goldOneAtZero = TierScore.score("GOLD", "I", 0);

        assertTrue(goldOneAtZero > goldTwoAtNearPromotion);
    }

    @Test
    void higherTierAlwaysBeatsLowerTierRegardlessOfDivision() {
        int silverOneMaxLp = TierScore.score("SILVER", "I", 0);
        int goldFourMinLp = TierScore.score("GOLD", "IV", 0);

        assertTrue(goldFourMinLp > silverOneMaxLp);
    }

    @Test
    void apexTierOrderingIgnoresRankField_evenThoughRiotSendsRankI() {
        // league-v4 returns rank="I" for Master/GM/Challenger too (not null) - the apex branch
        // must key off the tier name, not treat that "I" as a real division.
        int masterHighLp = TierScore.score("MASTER", "I", 300);
        int grandmasterLowLp = TierScore.score("GRANDMASTER", "I", 50);
        int challenger = TierScore.score("CHALLENGER", "I", 500);

        assertTrue(grandmasterLowLp > masterHighLp);
        assertTrue(challenger > grandmasterLowLp);
    }
}
