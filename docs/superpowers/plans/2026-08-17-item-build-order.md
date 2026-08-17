# 아이템 빌드 오더(구매/판매 순서) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `match-detail.html`에서 검색해서 들어온 소환사(본인) 행에만, 게임 중 아이템 구매/판매 순서를 시간(`m:ss`)과 함께 상시 표시한다.

**Architecture:** 매치 수집 시점(`MatchService.saveMatch()`)에 Riot Timeline API를 추가로 호출해 `ITEM_PURCHASED`/`ITEM_SOLD` 이벤트만 걸러 `Match.itemEventsJson`(매치 단위 JSON, 신규 컬럼)에 저장한다. 조회 시점에 `PageController`가 `focusPuuid` 쿼리파라미터(프로필 페이지 링크가 넘겨줌)로 어느 참가자가 "본인"인지 찾아, 저장된 이벤트를 그 참가자의 `participantId`로 필터링해 템플릿에 넘긴다. 새 라이브러리 없음, 새 REST 엔드포인트 없음.

**Tech Stack:** Spring MVC(Thymeleaf SSR), Spring `RestClient`(기존 `RiotApiClient` 패턴), Hibernate `ddl-auto: update`(마이그레이션 도구 없음, dev/prod 동일), Jackson 3(`tools.jackson`).

**Spec:** `docs/superpowers/specs/2026-08-17-item-build-order-design.md`

## Global Constraints

- 표시 위치는 정확히 한 곳: `match-detail.html`의 **포커스된(검색해서 들어온) 참가자 행만** — 나머지 9명, `profile.html` 매치 카드는 변경 없음(스펙 승인 조건)
- 골드 비용, 아이템 합성 트리 표시는 범위 밖
- 과거에 이미 수집된 매치(`itemEventsJson` null)에 대한 소급 백필 없음 — null이면 순서 줄 자체를 생략
- `ITEM_UNDO`는 처리하지 않음(반영 안 함, 스펙에 명시된 의도적 제외)
- Timeline API 호출 실패(429/5xx/타임아웃)가 매치 저장 자체를 막아서는 안 됨 — `itemEventsJson`을 null로 두고 계속 진행
- 새 프론트엔드 의존성 추가 금지 — Bootstrap 유틸리티 클래스(`opacity-50`)만 사용, 새 CSS 파일/클래스 없음

---

### Task 1: Riot Timeline API 클라이언트 + DTO

**Files:**
- Create: `src/main/java/com/lolstats/client/dto/RiotMatchTimelineResponse.java`
- Modify: `src/main/java/com/lolstats/client/RiotApiClient.java`
- Modify: `src/main/java/com/lolstats/client/RiotApiClientImpl.java`
- Test: `src/test/java/com/lolstats/client/RiotApiClientImplTest.java`

**Interfaces:**
- Produces: `RiotApiClient.getMatchTimeline(String matchId)` → `RiotMatchTimelineResponse` — Task 2가 이걸로 `MatchService`에서 호출함. `RiotMatchTimelineResponse.info().frames()`는 `List<RiotMatchTimelineFrame>`, 각 프레임의 `.events()`는 `List<tools.jackson.databind.JsonNode>`(이벤트 타입마다 필드가 달라 원시 JsonNode로 유지 — 파싱/필터링은 Task 2의 몫).

- [ ] **Step 1: 실패하는 테스트부터 작성**

`src/test/java/com/lolstats/client/RiotApiClientImplTest.java` 맨 아래(마지막 `}` 앞)에 추가:

```java
    @Test
    void getMatchTimeline_usesRegionalRoutingAndKeepsEventsAsRawNodes() {
        regionalServer.expect(requestTo(REGIONAL_URL + "/lol/match/v5/matches/KR_1111111111/timeline"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "info": {
                            "frames": [
                              {
                                "events": [
                                  {"type": "ITEM_PURCHASED", "timestamp": 65000, "participantId": 3, "itemId": 1055},
                                  {"type": "CHAMPION_KILL", "timestamp": 90000, "killerId": 3, "victimId": 8}
                                ]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        RiotMatchTimelineResponse result = client.getMatchTimeline("KR_1111111111");

        assertEquals(1, result.info().frames().size());
        assertEquals(2, result.info().frames().get(0).events().size());
        assertEquals("ITEM_PURCHASED", result.info().frames().get(0).events().get(0).path("type").asString(""));
        assertEquals(1055, result.info().frames().get(0).events().get(0).path("itemId").asInt(0));
        regionalServer.verify();
    }
```

파일 상단 import 블록에 추가:

```java
import com.lolstats.client.dto.RiotMatchTimelineResponse;
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew compileTestJava --no-daemon`
Expected: 컴파일 실패 — `RiotMatchTimelineResponse` 타입이 없고, `RiotApiClientImpl`에 `getMatchTimeline` 메서드가 없음

- [ ] **Step 3: DTO 작성**

`src/main/java/com/lolstats/client/dto/RiotMatchTimelineResponse.java` 새로 생성:

```java
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

    public record RiotMatchTimelineFrame(List<JsonNode> events) {
    }
}
```

- [ ] **Step 4: 인터페이스 + 구현체에 메서드 추가**

`src/main/java/com/lolstats/client/RiotApiClient.java`의 `RiotMatchResponse getMatchById(String matchId);` 바로 다음 줄에 추가:

```java

    RiotMatchTimelineResponse getMatchTimeline(String matchId);
```

파일 상단 import에 추가:

```java
import com.lolstats.client.dto.RiotMatchTimelineResponse;
```

`src/main/java/com/lolstats/client/RiotApiClientImpl.java`의 `getMatchById` 메서드(`RiotMatchResponse getMatchById(String matchId) { ... }`) 바로 다음에 추가:

```java

    @Override
    public RiotMatchTimelineResponse getMatchTimeline(String matchId) {
        return withRetry(() -> regionalClient.get()
                .uri("/lol/match/v5/matches/{matchId}/timeline", matchId)
                .retrieve()
                .body(RiotMatchTimelineResponse.class));
    }
```

파일 상단 import에 추가:

```java
import com.lolstats.client.dto.RiotMatchTimelineResponse;
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.lolstats.client.RiotApiClientImplTest" --no-daemon`
Expected: PASS (전체 - 기존 테스트 포함)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/lolstats/client/dto/RiotMatchTimelineResponse.java src/main/java/com/lolstats/client/RiotApiClient.java src/main/java/com/lolstats/client/RiotApiClientImpl.java src/test/java/com/lolstats/client/RiotApiClientImplTest.java
git commit -m "feat: Add Riot Timeline API client"
```

---

### Task 2: 매치 수집 시점에 아이템 구매/판매 이벤트 저장

**Files:**
- Create: `src/main/java/com/lolstats/dto/ItemEvent.java`
- Modify: `src/main/java/com/lolstats/domain/Match.java`
- Modify: `src/main/java/com/lolstats/service/MatchService.java`
- Test: `src/test/java/com/lolstats/service/MatchServiceTest.java`

**Interfaces:**
- Consumes: Task 1의 `RiotApiClient.getMatchTimeline(String)` → `RiotMatchTimelineResponse`
- Produces: `Match.itemEventsJson`(String, nullable, JSON) — `List<ItemEvent>` 직렬화 결과. `ItemEvent(int participantId, int itemId, String type, long timestampMs)`. Task 3이 이 컬럼을 읽어서 `ItemEvent[].class`로 역직렬화함.

- [ ] **Step 1: 실패하는 테스트부터 작성**

`src/test/java/com/lolstats/service/MatchServiceTest.java`의 `sampleMatch` 메서드 바로 다음에 헬퍼 추가:

```java

    private static RiotMatchTimelineResponse sampleTimeline() {
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode purchased = mapper.readTree("""
                {"type":"ITEM_PURCHASED","timestamp":65000,"participantId":1,"itemId":1055}
                """);
        JsonNode sold = mapper.readTree("""
                {"type":"ITEM_SOLD","timestamp":120000,"participantId":1,"itemId":1055}
                """);
        JsonNode kill = mapper.readTree("""
                {"type":"CHAMPION_KILL","timestamp":90000,"killerId":1,"victimId":6}
                """);
        return new RiotMatchTimelineResponse(new RiotMatchTimelineResponse.RiotMatchTimelineInfo(
                List.of(new RiotMatchTimelineResponse.RiotMatchTimelineFrame(List.of(purchased, sold, kill)))));
    }
```

파일 마지막(닫는 `}` 앞)에 테스트 두 개 추가:

```java

    @Test
    void collectMatches_savesItemEventsJson_purchasedAndSoldOnly() {
        when(riotApiClient.getMatchById("KR_1")).thenReturn(sampleMatch("KR_1"));
        when(riotApiClient.getMatchTimeline("KR_1")).thenReturn(sampleTimeline());

        service.collectMatches(List.of("KR_1"), () -> {
        });

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        String json = matchCaptor.getValue().getItemEventsJson();
        assertTrue(json.contains("ITEM_PURCHASED"));
        assertTrue(json.contains("ITEM_SOLD"));
        assertTrue(json.contains("1055"));
        assertFalse(json.contains("CHAMPION_KILL"));
    }

    @Test
    void collectMatches_savesMatchEvenWhenTimelineFetchFails() {
        when(riotApiClient.getMatchById("KR_1")).thenReturn(sampleMatch("KR_1"));
        when(riotApiClient.getMatchTimeline("KR_1")).thenThrow(new RuntimeException("Riot API down"));

        MatchService.CollectionResult result = service.collectMatches(List.of("KR_1"), () -> {
        });

        assertEquals(1, result.savedCount());
        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertNull(matchCaptor.getValue().getItemEventsJson());
    }
```

파일 상단 import 블록을 다음으로 교체(기존 import에 4줄 추가):

```java
import com.lolstats.client.RiotApiClient;
import com.lolstats.client.dto.RiotMatchResponse;
import com.lolstats.client.dto.RiotMatchTimelineResponse;
import com.lolstats.domain.Match;
import com.lolstats.domain.MatchParticipant;
import com.lolstats.repository.MatchParticipantRepository;
import com.lolstats.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.lolstats.service.MatchServiceTest" --no-daemon`
Expected: 컴파일 실패 — `getItemEventsJson()`이 `Match`에 없고, `RiotApiClient.getMatchTimeline`이 (아직 목 설정은 가능하지만) `MatchService`가 호출하지 않아 `assertTrue(json.contains(...))`에서 `json`이 `null`이라 NPE, 또는 애초에 `getItemEventsJson()` 미존재로 컴파일 실패

- [ ] **Step 3: `Match`에 컬럼 추가**

`src/main/java/com/lolstats/domain/Match.java`의 `@Column(name = "queue_type") private String queueType;` 다음 줄에 추가:

```java

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "item_events_json", columnDefinition = "json")
    private String itemEventsJson;
```

(전체 경로로 쓴 이유: 이 파일엔 아직 두 import가 없음. 대신 파일 상단 import 블록에 아래 두 줄을 추가하고 필드는 짧게 써도 됨 — 취향껏, 둘 다 동작은 동일)

```java
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
```

- [ ] **Step 4: `MatchService`가 Timeline을 가져와 필터링·저장하도록 수정**

`src/main/java/com/lolstats/service/MatchService.java`의 `collectMatches` 메서드 내부, `saveMatch(riotApiClient.getMatchById(matchId));` 줄을 다음으로 교체:

```java
                RiotMatchResponse response = riotApiClient.getMatchById(matchId);
                saveMatch(response, fetchItemEventsJson(matchId));
```

`saveMatch` 메서드 전체를 다음으로 교체:

```java
    private void saveMatch(RiotMatchResponse response, String itemEventsJson) {
        transactionTemplate.executeWithoutResult(status -> {
            Match match = matchRepository.save(Match.builder()
                    .riotMatchId(response.metadata().matchId())
                    .gameCreation(Instant.ofEpochMilli(response.info().gameCreation()))
                    .gameDuration(response.info().gameDuration())
                    .queueType(String.valueOf(response.info().queueId()))
                    .itemEventsJson(itemEventsJson)
                    .build());

            List<MatchParticipant> participants = response.info().participants().stream()
                    .map(p -> toParticipant(match, p))
                    .toList();
            matchParticipantRepository.saveAll(participants);
        });
    }

    // Timeline은 매치 상세와 별개의 Riot API 호출 - 여기서 실패(429/5xx/타임아웃)해도 매치 저장
    // 자체를 막으면 안 되므로 null을 돌려주고 계속 진행한다(PageController는 null이면 빌드 오더
    // 줄을 그냥 생략함, 에러 아님).
    private String fetchItemEventsJson(String matchId) {
        RiotMatchTimelineResponse timeline;
        try {
            timeline = riotApiClient.getMatchTimeline(matchId);
        } catch (RuntimeException e) {
            log.warn("Failed to fetch match timeline for {} - item build order will be unavailable", matchId, e);
            return null;
        }
        // Tests that don't stub getMatchTimeline() get null back from Mockito - same "no
        // timeline data" outcome as a caught failure above, not a separate case to test.
        if (timeline == null) {
            return null;
        }
        List<ItemEvent> events = timeline.info().frames().stream()
                .flatMap(f -> f.events().stream())
                .filter(e -> "ITEM_PURCHASED".equals(e.path("type").asString(""))
                        || "ITEM_SOLD".equals(e.path("type").asString("")))
                .map(e -> new ItemEvent(
                        e.path("participantId").asInt(0),
                        e.path("itemId").asInt(0),
                        e.path("type").asString(""),
                        e.path("timestamp").asLong(0)))
                .toList();
        return writeJson(events);
    }
```

파일 상단에 `@Slf4j` 롬복 애노테이션과 import 추가 - `public class MatchService {` 바로 위 줄에:

```java
@lombok.extern.slf4j.Slf4j
```

(다른 클래스들처럼 짧은 import로 쓰고 싶으면 상단 import 블록에 `import lombok.extern.slf4j.Slf4j;` 추가하고 클래스 위에 `@Slf4j`만 붙여도 동일)

파일 상단 import 블록에 추가:

```java
import com.lolstats.client.dto.RiotMatchTimelineResponse;
import com.lolstats.dto.ItemEvent;
import tools.jackson.databind.JsonNode;
```

- [ ] **Step 5: `ItemEvent` DTO 작성**

`src/main/java/com/lolstats/dto/ItemEvent.java` 새로 생성:

```java
package com.lolstats.dto;

// Match.itemEventsJson에 매치 단위로 저장되는 구매/판매 이벤트 하나. participantId는 1~10
// (Riot Timeline의 participantId 그대로) - PageController가 focus 소환사의 participantId로
// 필터링할 때 씀.
public record ItemEvent(int participantId, int itemId, String type, long timestampMs) {
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests "com.lolstats.service.MatchServiceTest" --no-daemon`
Expected: PASS (전체)

- [ ] **Step 7: 전체 스위트 회귀 확인**

Run: `./gradlew test --rerun-tasks --no-daemon`
Expected: BUILD SUCCESSFUL — `saveMatch` 시그니처가 바뀌었지만 호출부는 `collectMatches` 한 곳뿐(사전 확인: `grep -rn "saveMatch("` 결과 정의부+호출부 이 두 곳뿐)

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/lolstats/dto/ItemEvent.java src/main/java/com/lolstats/domain/Match.java src/main/java/com/lolstats/service/MatchService.java src/test/java/com/lolstats/service/MatchServiceTest.java
git commit -m "feat: Save item purchase/sell events from Riot Timeline API"
```

---

### Task 3: `PageController`가 포커스 참가자의 빌드 오더를 조립

**Files:**
- Modify: `src/main/java/com/lolstats/controller/PageController.java:150-202`(`matchDetail`, `toParticipantView`), `:244-252`(`ParticipantView`/`ItemView`)

**Interfaces:**
- Consumes: Task 2의 `Match.itemEventsJson`(nullable String), `ItemEvent(participantId, itemId, type, timestampMs)`
- Produces: `PageController.ItemEventView(String imageUrl, String name, String type, String timeLabel)` — Task 4의 `match-detail.html`이 `p.itemEvents`로 순회함. `ParticipantView.itemEvents: List<ItemEventView>`(포커스 아니면 빈 리스트).

- [ ] **Step 1: `/matches/{riotMatchId}`에 `focusPuuid` 파라미터 추가 + 조립 로직 교체**

`src/main/java/com/lolstats/controller/PageController.java:150-166`(현재 `matchDetail` 메서드 전체)을 다음으로 교체:

```java
    @GetMapping("/matches/{riotMatchId}")
    public String matchDetail(
            @PathVariable String riotMatchId,
            @RequestParam(required = false) String focusPuuid,
            Model model) {
        Match match = matchRepository.findByRiotMatchId(riotMatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "match not found: " + riotMatchId));
        List<MatchParticipant> participants = matchParticipantRepository.findByMatchId(match.getId());
        List<ItemEvent> itemEvents = parseItemEvents(match.getItemEventsJson());

        model.addAttribute("riotMatchId", match.getRiotMatchId());
        model.addAttribute("playedAt", PLAYED_AT_FORMAT.format(match.getGameCreation()));
        model.addAttribute("duration", formatDuration(match.getGameDuration()));
        model.addAttribute("queueType", QueueNames.displayName(match.getQueueType()));
        // Riot's response orders participants team100 (first 5) then team200 (last 5), and
        // saveAll()/findByMatchId preserve that order - that order IS the Timeline
        // participantId (1-based position = participantId). Kept as limit/skip (not
        // subList(0,5)/subList(5,10)) so a match saved with fewer than 10 participants still
        // renders instead of throwing IndexOutOfBoundsException.
        List<ParticipantView> views = toParticipantViews(participants, focusPuuid, itemEvents);
        model.addAttribute("team1", views.stream().limit(5).toList());
        model.addAttribute("team2", views.stream().skip(5).toList());
        return "match-detail";
    }

    private List<ParticipantView> toParticipantViews(
            List<MatchParticipant> participants, String focusPuuid, List<ItemEvent> itemEvents) {
        return java.util.stream.IntStream.range(0, participants.size())
                .mapToObj(i -> {
                    MatchParticipant p = participants.get(i);
                    int participantId = i + 1;
                    List<ItemEventView> events = p.getPuuid().equals(focusPuuid)
                            ? toItemEventViews(itemEvents, participantId)
                            : List.of();
                    return toParticipantView(p, events);
                })
                .toList();
    }

    private List<ItemEventView> toItemEventViews(List<ItemEvent> itemEvents, int participantId) {
        return itemEvents.stream()
                .filter(e -> e.participantId() == participantId)
                .map(e -> new ItemEventView(
                        dataDragonService.getItem(e.itemId()).map(DataDragonService.ItemInfo::imageUrl).orElse(null),
                        dataDragonService.getItem(e.itemId()).map(DataDragonService.ItemInfo::name).orElse("?"),
                        e.type(),
                        formatDuration((int) (e.timestampMs() / 1000))))
                .toList();
    }

    private List<ItemEvent> parseItemEvents(String itemEventsJson) {
        if (itemEventsJson == null) {
            return List.of();
        }
        return List.of(objectMapper.readValue(itemEventsJson, ItemEvent[].class));
    }
```

- [ ] **Step 2: `toParticipantView`에 `itemEvents` 파라미터를 실어 나르는 오버로드 추가**

`src/main/java/com/lolstats/controller/PageController.java:178`(현재 `private ParticipantView toParticipantView(MatchParticipant p) {`로 시작하는 메서드) 전체를 다음으로 교체:

```java
    private ParticipantView toParticipantView(MatchParticipant p) {
        return toParticipantView(p, List.of());
    }

    private ParticipantView toParticipantView(MatchParticipant p, List<ItemEventView> itemEvents) {
        String championName = dataDragonService.getChampion(p.getChampionId())
                .map(DataDragonService.ChampionInfo::name).orElse("?");
        String championImageUrl = dataDragonService.getChampion(p.getChampionId())
                .map(DataDragonService.ChampionInfo::imageUrl).orElse(null);

        List<ItemView> items = List.of(objectMapper.readValue(p.getItemsJson(), Integer[].class)).stream()
                .map(id -> id == 0 ? null : dataDragonService.getItem(id)
                        .map(item -> new ItemView(item.imageUrl(), item.name(), item.description()))
                        .orElse(null))
                .toList();

        JsonNode perks = objectMapper.readTree(p.getRunesJson());
        String keystoneIconUrl = dataDragonService.getRuneIconUrl(
                perks.path("styles").path(0).path("selections").path(0).path("perk").asInt(0)).orElse(null);
        String secondaryStyleIconUrl = dataDragonService.getRuneIconUrl(
                perks.path("styles").path(1).path("style").asInt(0)).orElse(null);

        return new ParticipantView(
                p.getGameName(), p.getTagLine(), championName, championImageUrl, p.getTeamPosition(),
                p.getKills(), p.getDeaths(), p.getAssists(), p.getWin(),
                dataDragonService.getSpell(p.getSpell1Id()).map(DataDragonService.SpellInfo::imageUrl).orElse(null),
                dataDragonService.getSpell(p.getSpell2Id()).map(DataDragonService.SpellInfo::imageUrl).orElse(null),
                items, keystoneIconUrl, secondaryStyleIconUrl, itemEvents);
    }
```

- [ ] **Step 3: `ParticipantView`에 `itemEvents` 필드 + `ItemEventView` 레코드 추가**

`src/main/java/com/lolstats/controller/PageController.java:244-249`(현재 `ParticipantView` 레코드)을 다음으로 교체:

```java
    public record ParticipantView(
            String gameName, String tagLine, String championName, String championImageUrl, String teamPosition,
            Integer kills, Integer deaths, Integer assists, Boolean win,
            String spell1ImageUrl, String spell2ImageUrl, List<ItemView> items,
            String keystoneIconUrl, String secondaryStyleIconUrl, List<ItemEventView> itemEvents) {
    }

    public record ItemEventView(String imageUrl, String name, String type, String timeLabel) {
    }
```

- [ ] **Step 4: import 추가**

파일 상단 import 블록에 추가:

```java
import com.lolstats.dto.ItemEvent;
import org.springframework.web.bind.annotation.RequestParam;
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew compileJava --no-daemon`
Expected: BUILD SUCCESSFUL (템플릿은 아직 안 고쳐도 Java 컴파일은 통과 - `match-detail.html`이 `p.itemEvents`를 안 써도 Thymeleaf는 런타임 평가라 문제 없음)

- [ ] **Step 6: 전체 스위트 회귀 확인**

Run: `./gradlew test --rerun-tasks --no-daemon`
Expected: BUILD SUCCESSFUL — `PageController`는 전용 단위 테스트 없음(이 프로젝트 기존 관행), 다른 서비스/컨트롤러 테스트가 깨지지 않는지만 확인

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/lolstats/controller/PageController.java
git commit -m "feat: Build focused participant's item event timeline in match-detail"
```

---

### Task 4: 템플릿에 빌드 오더 줄 표시

**Files:**
- Modify: `src/main/resources/templates/profile.html:53`
- Modify: `src/main/resources/templates/match-detail.html:34-40`(팀1), `:67-73`(팀2)

- [ ] **Step 1: `profile.html` 매치 카드 링크에 `focusPuuid` 추가**

`src/main/resources/templates/profile.html:53`을 다음으로 교체:

```html
            <a th:each="m : ${matches}" th:href="@{/matches/{id}(id=${m.riotMatchId}, focusPuuid=${summoner.puuid})}"
```

- [ ] **Step 2: `match-detail.html` 팀1 아이템 셀에 빌드 오더 줄 추가**

`src/main/resources/templates/match-detail.html:34-40`(팀1 아이템 `<td>`)을 다음으로 교체:

```html
                <td>
                    <span th:each="item : ${p.items}">
                        <img th:if="${item}" th:src="${item.imageUrl}" width="24" height="24" class="rounded"
                             alt="아이템" data-bs-toggle="popover" data-bs-html="true" data-bs-trigger="hover focus"
                             th:attr="data-bs-title=${item.name},data-bs-content=${item.description}">
                    </span>
                    <div th:if="${not #lists.isEmpty(p.itemEvents)}" class="d-flex flex-wrap align-items-center gap-1 mt-1">
                        <span th:each="ev : ${p.itemEvents}" class="d-flex align-items-center"
                              th:classappend="${ev.type == 'ITEM_SOLD'} ? 'opacity-50' : ''">
                            <img th:src="${ev.imageUrl}" width="16" height="16" class="rounded" th:alt="${ev.name}">
                            <small class="text-muted ms-1" th:text="${ev.timeLabel}">0:00</small>
                        </span>
                    </div>
                </td>
```

- [ ] **Step 3: `match-detail.html` 팀2 아이템 셀도 동일하게 교체**

`src/main/resources/templates/match-detail.html:67-73`(팀2 아이템 `<td>`, 팀1과 동일한 마크업)을 다음으로 교체:

```html
                <td>
                    <span th:each="item : ${p.items}">
                        <img th:if="${item}" th:src="${item.imageUrl}" width="24" height="24" class="rounded"
                             alt="아이템" data-bs-toggle="popover" data-bs-html="true" data-bs-trigger="hover focus"
                             th:attr="data-bs-title=${item.name},data-bs-content=${item.description}">
                    </span>
                    <div th:if="${not #lists.isEmpty(p.itemEvents)}" class="d-flex flex-wrap align-items-center gap-1 mt-1">
                        <span th:each="ev : ${p.itemEvents}" class="d-flex align-items-center"
                              th:classappend="${ev.type == 'ITEM_SOLD'} ? 'opacity-50' : ''">
                            <img th:src="${ev.imageUrl}" width="16" height="16" class="rounded" th:alt="${ev.name}">
                            <small class="text-muted ms-1" th:text="${ev.timeLabel}">0:00</small>
                        </span>
                    </div>
                </td>
```

- [ ] **Step 4: 전체 스위트 회귀 확인**

Run: `./gradlew test --rerun-tasks --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 로컬에서 실제 브라우저로 확인**

로컬 dev 서버(`./gradlew bootRun`, MySQL/Redis 컨테이너 기동 상태, `RIOT_API_KEY` 등 `.env` 로드)에서, 이미 매치 기록이 있는 소환사의 프로필 페이지 → 매치 카드 클릭 → 매치 상세 페이지 진입:

Expected:
- 본인 행에만 최종 아이템 줄 아래에 구매/판매 순서 줄(아이콘 + `m:ss`)이 보임
- 판매 아이템은 흐리게(`opacity-50`) 표시됨
- 나머지 9명 행에는 순서 줄이 아예 없음
- `itemEventsJson`이 없는(이 기능 배포 전에 수집된) 오래된 매치는 본인 행에도 순서 줄이 안 뜸(에러 없이 조용히 생략)
- **participantId 매핑이 맞는지 확인**: 순서 줄의 마지막 몇 개 아이콘(판매되지 않은 것)이 같은 행 위쪽의 최종 아이템 아이콘과 일치하는지 눈으로 대조. 다르면 다른 참가자의 빌드 오더가 잘못 붙은 것 — 화면에 아이콘이 뜨는 것만으로는 이 버그를 못 잡음(오늘 아이템 이름 누락이 그렇게 놓쳤던 것과 같은 종류의 실수)

- [ ] **Step 6: 커밋**

```bash
git add src/main/resources/templates/profile.html src/main/resources/templates/match-detail.html
git commit -m "feat: Show item build order for the focused participant"
```

- [ ] **Step 7: 배포 (사용자 확인 후 진행)**

여기까지는 로컬 커밋만 되어 있는 상태. `git push origin master`로 CI/CD를 트리거하고 실사이트에서 최종 확인하는 건, 이전 아이템 호버 툴팁 작업 때처럼 사용자가 명시적으로 요청할 때 진행한다 — 플랜 완료 시점에 자동으로 push/배포하지 않음.
