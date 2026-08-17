package com.lolstats.client.dto;

import tools.jackson.databind.JsonNode;

import java.util.List;

// match-v5 timeline response (regional routing). 이벤트 타입마다 필드가 다르므로(ITEM_PURCHASED는
// itemId를, CHAMPION_KILL은 killerId/victimId를 가짐) 하나의 레코드로 강타입 매핑하지 않고 원시
// JsonNode로 유지 - RiotMatchResponse의 perks 필드와 같은 패턴. 관심 있는 필드만 호출부에서
// path(...)로 꺼내 쓴다.
public record RiotMatchTimelineResponse(RiotMatchTimelineInfo info) {

    public record RiotMatchTimelineInfo(List<RiotMatchTimelineFrame> frames) {
    }

    public record RiotMatchTimelineFrame(JsonNode events) {
    }
}
