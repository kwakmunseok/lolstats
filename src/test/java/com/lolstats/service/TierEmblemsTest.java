package com.lolstats.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TierEmblemsTest {

    @Test
    void buildsPathFromTierString() {
        assertEquals("/images/tier-emblems/CHALLENGER.png", TierEmblems.imageUrl("CHALLENGER"));
    }

    @Test
    void unranked_returnsNull() {
        assertNull(TierEmblems.imageUrl(null));
    }
}
