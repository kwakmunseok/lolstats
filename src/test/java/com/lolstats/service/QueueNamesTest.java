package com.lolstats.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueueNamesTest {

    @Test
    void displayName_mapsKnownRiftQueueIds() {
        assertEquals("솔로랭크", QueueNames.displayName("420"));
        assertEquals("자유랭크", QueueNames.displayName("440"));
        assertEquals("일반(드래프트)", QueueNames.displayName("400"));
        assertEquals("일반(블라인드)", QueueNames.displayName("430"));
    }

    @Test
    void displayName_fallsBackToRawIdForUnknownQueue() {
        assertEquals("450", QueueNames.displayName("450"));
    }
}
