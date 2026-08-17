# 아이템 빌드 오더(구매/판매 순서) — 설계

> 사용자 피드백(2026-08-17): "원래 롤 관련 사이트는 최종 아이템 뿐만 아니라, 시간 순으로 무슨 아이템을 갔는지도 기록해줌."

## 배경

`match-detail.html`은 현재 각 참가자의 **최종** 아이템 7슬롯만 아이콘으로 보여준다. 게임 중 언제 무슨 아이템을 사고 팔았는지(빌드 오더)는 전혀 기록/표시되지 않는다. Riot Match-V5 상세 응답(`RiotMatchResponse`)엔 최종 스냅샷만 있고, 구매/판매 이벤트와 타임스탬프는 별도의 **Timeline API**(`/lol/match/v5/matches/{matchId}/timeline`)에만 존재한다.

## 범위

- 표시 위치: `match-detail.html`에서 **검색해서 들어온 소환사(본인) 행에만** — 다른 9명은 지금처럼 최종 아이템만.
- 표시 내용: 구매(`ITEM_PURCHASED`)와 판매(`ITEM_SOLD`) 이벤트를 시간순으로 아이콘 + `m:ss` 라벨로 상시 표시(토글 없음).
- 데이터 수집: 매치 수집 시점(`MatchService.saveMatch()`)에 Timeline API를 함께 호출해 미리 DB에 저장 — 페이지 조회 시점 실시간 호출 아님.
- 제외: 골드 비용, 아이템 합성 트리(빌드 경로) 표시 — 이번 범위는 "무슨 아이템을 언제 갔는지"까지.
- 제외: 이미 수집된 과거 매치에 대한 소급 백필 — 이 기능 배포 이후 새로 수집되는 매치부터 적용.

## 알려진 트레이드오프 (사용자 승인 완료)

매치 하나당 Riot API 호출이 1회 늘어난다(상세 조회 1콜 → 상세+타임라인 2콜). 백그라운드 수집/크롤러의 처리 속도가 그만큼 느려질 수 있음 — 사용자 확인 후 진행하기로 함.

## 구현 방식

### 데이터 흐름

1. **`RiotApiClient`**: `RiotMatchTimelineResponse getMatchTimeline(String matchId)` 메서드 추가 — `/lol/match/v5/matches/{matchId}/timeline` (기존 `getMatchById`와 같은 리전 라우팅).
2. **신규 DTO** `client/dto/RiotMatchTimelineResponse.java`: `info.frames[].events[]`만 매핑. 관심 이벤트는 `type`이 `ITEM_PURCHASED` 또는 `ITEM_SOLD`인 것만 — 그 외 이벤트 타입(킬, 와드 등)은 필드 자체를 매핑하지 않아 자연히 무시됨(Jackson 미지정 필드는 이 프로젝트 기존 DTO들처럼 무시).
   - 이벤트 필드: `type`(String), `participantId`(int, 1~10), `itemId`(int), `timestamp`(long, ms).
3. **`MatchService.saveMatch()`**: `getMatchById()` 호출 직후 `getMatchTimeline()`도 호출. 응답에서 `ITEM_PURCHASED`/`ITEM_SOLD` 이벤트만 걸러 시간순으로 정렬한 뒤, `Match.itemEventsJson`(새 컬럼)에 `List<ItemEvent>`(participantId, itemId, type, timestampMs)로 직렬화 — **참가자별로 쪼개 저장하지 않고 매치 하나에 전체 이벤트를 저장**(기존 `itemsJson`/`runesJson`이 참가자 단위로 저장되는 것과 다른 점 — 여기선 어느 참가자를 포커스할지 조회 시점에만 정해지므로 매치 단위 저장이 더 단순함).
   - `participantId`(1~10)는 `MatchParticipant`에 별도 컬럼으로 추가하지 않는다 — `response.info().participants()`의 배열 순서가 곧 participantId 순서라는 기존 불변식(PageController의 "Riot's response orders them team100 (first 5) then team200 (last 5)" 주석, `saveAll()`/`findByMatchId()`가 순서 보존)을 그대로 재사용해, 조회 시점에 `team1`/`team2` 리스트의 1-based 인덱스로 매칭한다.
4. **`Match` 엔티티**: `itemEventsJson`(TEXT, nullable) 컬럼 추가.
5. **`PageController`**:
   - `matchDetail()`에 `@RequestParam(required = false) String focusPuuid` 추가.
   - 저장된 `itemEventsJson`을 파싱하고, `focusPuuid`와 일치하는 참가자의 팀 내 1-based 순번(team100이면 그대로, team200이면 +5)으로 `participantId`를 걸러 `List<ItemEventView>`(아이콘 URL, 아이템 이름, `type`, `timeLabel`)를 만든다. `timeLabel`은 기존 `formatDuration(Integer seconds)`를 재사용(`timestampMs / 1000`).
   - `ParticipantView`에 `List<ItemEventView> itemEvents`(포커스 아니면 빈 리스트) 필드 추가.
6. **`profile.html`**: 매치 카드 링크를 `@{/matches/{id}(id=${m.riotMatchId}, focusPuuid=${summoner.puuid})}`로 변경(현재는 `id`만 있음).
7. **`match-detail.html`**: 각 참가자 행의 기존 최종 아이템 아이콘 줄 아래에, `itemEvents`가 비어있지 않을 때만 순서 줄 추가 — 작은 아이콘(16~20px) + `m:ss` 텍스트를 가로로 나열, 판매(`SOLD`)는 아이콘에 흐림/취소선 스타일로 구분(신규 CSS 클래스 하나).

### 에러 처리

- Timeline 호출이 실패(429/5xx/타임아웃)해도 매치 저장 자체(참가자 포함)는 그대로 진행 — `itemEventsJson`을 null로 두고 계속. 기존 `getMatchById`의 429 처리(부분 실패를 전체 실패로 만들지 않음)와 같은 톤.
- `itemEventsJson`이 null인 매치(과거 매치, 또는 Timeline 호출 실패) → 순서 줄 자체를 렌더링하지 않음(에러 아님, 조용히 스킵).
- `focusPuuid`가 없거나 해당 매치 10명 중에 없으면(잘못된 링크, 직접 URL 접근 등) 아무도 포커스되지 않은 기존 화면 그대로.

## 테스트

- **`MatchServiceTest`**: Timeline 응답을 스텁해서 `itemEventsJson`에 이벤트가 시간순·타입 정확하게 직렬화되는지 검증. Timeline 호출이 예외를 던져도 매치/참가자 저장은 성공하는 케이스 추가(기존 429 테스트와 같은 패턴).
- **화면 검증**: 로컬에서 실제 매치로 프로필 → 매치 상세 진입 시 본인 행에만 구매/판매 순서 줄이 뜨는지, 다른 9명에겐 안 뜨는지 Playwright/브라우저로 확인(이 프로젝트 기존 관행 — PageController 단위 테스트 없음).

## 이번 범위 밖 (명시적 제외)

- 골드 비용, 아이템 합성 트리 표시
- 과거에 이미 수집된 매치에 대한 소급 백필
- 포커스 소환사 외 나머지 9명의 빌드 오더 표시
- 순서 줄 토글/접기 UI (상시 표시로 결정됨)
- `ITEM_UNDO`(구매 직후 취소) 처리 — 반영 안 함. 취소된 구매도 그대로 순서에 남는 드문 엣지케이스가 생길 수 있으나, 실사용 영향이 작아 이번 범위에서 의도적으로 제외
