package com.lolstats.dto;

// Match.itemEventsJson에 매치 단위로 저장되는 구매/판매 이벤트 하나. participantId는 1~10
// (Riot Timeline의 participantId 그대로) - PageController가 focus 소환사의 participantId로
// 필터링할 때 씀.
public record ItemEvent(int participantId, int itemId, String type, long timestampMs) {
}
