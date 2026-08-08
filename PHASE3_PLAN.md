# Phase 3 상세 작업 계획서 (데이터/통계)

> [PROJECT_PLAN.md](./PROJECT_PLAN.md) §4 Phase 3를 실행 단위로 쪼갠 작업 분해서(WBS). [PHASE2_PLAN.md](./PHASE2_PLAN.md)와 동일한 형식.
> 예산: **22~26h** (PROJECT_PLAN.md §10). Phase 1/2와 달리 신규 인프라·의존성 없음 — 기존 MySQL/JPA 구조 위에 집계 로직 + 테이블 1개(TIER_HISTORY) 추가가 전부.
> **상태: 미착수 — 이 문서가 최초 계획서**(PHASE2_PLAN.md §0.1에서 예고됨). 아래 §5 "미결 사항"은 Phase 2 §5 "결정 사항"과 달리 아직 grilling을 거치지 않은 열린 질문 — 착수 전 확정 또는 기본값 그대로 진행할지 확인 필요.

## 0. 착수 전 확인 사항

- [ ] 신규 의존성/인프라 없음 확인 — `TIER_HISTORY`는 `ddl-auto: update`(기존 방식, Flyway 등 마이그레이션 도구 미사용)로 다음 부팅 시 자동 생성
- [ ] §5 미결 사항 먼저 훑고 시작(특히 champion-stats 응답 구조) — 기본값대로 진행해도 무방하지만 안 보고 시작하면 Task 1 다 짜고 나서 뒤집을 수 있음

## 0.1 진행 현황 & 재개 방법

- Phase 1·2 완료. 크롤러(Task 1~4) 별도 커밋 완료, 데이터 수집 진행 중(2026-08-06 기준 Master 92%, Challenger/Grandmaster 100%, 백그라운드 계속 가동 — 크롤러와 개발 서버 동시 가동 금지, §4 크롤러 조건①).
- 다음 세션 시작 시: §5 미결 사항 확정(또는 기본값 수용) → Task 1부터 순서대로.

## 1. 이 문서 범위에 포함되지 않는 것

- 배포(EC2/도메인/HTTPS/CI-CD) — PROJECT_PLAN.md §9
- Phase 4 상대 챔피언 승률(조건부, go/no-go 이후), Phase 5 JWT/즐겨찾기
- 크롤러 경로에 TIER_HISTORY 적재 연결 — §5 미결 사항, 기본값은 "안 함"

---

## 2. 작업 순서 (의존성 기준)

```
Task 1: 통계 집계 서비스           Task 2: 티어 이력 스냅샷 적재
(챔피언/승률/폼 — 큐 필터는         (TIER_HISTORY 엔티티 +
 MatchService.RIFT_QUEUE_TYPES      SummonerService 훅)
 그대로 재사용, 신규 매핑 불요)             │
      │                                   ▼
      │                          Task 3: 티어 이력 조회 API
      │                                   │
      └───────────────┬───────────────────┘
                       ▼
             Task 4: 화면 (챔피언 통계 탭 + 티어 이력 탭)
                       │
                       ▼
             Task 5: 테스트 정리 / 누락분 보강
```

Task 1과 Task 2는 서로 독립(하나는 읽기 전용 집계, 하나는 검색 시점 스냅샷 적재)이라 순서 안 가리고 아무거나 먼저 해도 됨. Task 3은 Task 2가 만든 엔티티가 있어야 하고, Task 4(화면)는 Task 1·Task 3 API가 둘 다 있어야 탭을 채울 수 있음.

**중요 발견 (착수 전 코드 확인 결과)**: `MatchService.java:26`에 이미 `RIFT_QUEUE_TYPES = List.of("400","420","430","440")`(Normal Draft/Blind + Ranked Solo/Flex)가 정의돼 있고, 그 위 주석이 정확히 "Task 4 decision: raw queueId, no name mapping until Phase 3"라고 명시. 즉 "랭크/드래프트 큐만 집계" **필터**에는 신규 코드가 필요 없음 — 이 상수를 그대로 재사용하면 됨.

큐 **표시명** 매핑(420 → "솔로랭크" 등)은 필터와 별개 문제지만, §5 결정에 따라 이번 Phase 범위로 확정 — Task 1에서 같이 처리(아래 참고).

---

## 3. 상세 작업 항목 (WBS)

### 1. 통계 집계 서비스 (승률/최근 폼/챔피언별) + 큐 표시명 매핑 — 5~6h ✅ 완료

- [x] `MatchParticipantRepository`에 신규 쿼리 추가: `List<MatchParticipant> findByPuuidAndMatch_QueueTypeInOrderByMatch_GameCreationDesc(String puuid, Collection<String> queueTypes)` — 큐 필터는 `MatchService.RIFT_QUEUE_TYPES` 그대로 전달
- [x] `ChampionStatsService`(신규): 위 리스트를 스트림으로 3가지 집계 — ① 전체 승/패 → winRate ② `game_creation` 내림차순 최근 폼 배열(승/패) ③ `champion_id` GROUP BY → 챔피언별 games/wins/winRate/평균 KDA(`ΣK+ΣA)/ΣD`, `ΣD==0`이면 `ΣK+ΣA`로 대체 - 0으로 나누기 방지). **상한 로직 없음(의도적)** — 계획 초안엔 "소환사당 20건 캡이 있어 상한 불요"라 적었는데, 라이브 확인 중 크롤러가 교차 수집한 인기 소환사는 참가자 행이 400건 이상도 나옴(자신의 20건 자기수집 캡과 별개로, 다른 소환사 백필이 주워담은 같은 매치의 참가자 행도 쌓임) — 그래도 인메모리 집계엔 문제없는 크기라 배치 테이블 불필요 결론은 그대로 유지
- [x] DTO `ChampionStatsResponse`(games, overallWinRate, recentForm: List\<Boolean\>, perChampion: List\<ChampionStatRow{championId, games, wins, winRate, avgKda}\>) — §5 미결 사항 기본값(한 응답에 다 묶음) 적용
- [x] `GET /api/summoners/{summonerId}/champion-stats` — `MatchController`에 추가(챔피언 통계는 매치 데이터 집계라 소환사 코어 데이터를 다루는 `SummonerController`보다 매치 관련 엔드포인트들과 같은 컨트롤러가 적합하다고 판단해 배치 변경)
- [x] 테스트: 랭크+ARAM 섞인 매치에서 ARAM 제외 확인, 챔피언 그룹핑/승률 계산 검증, 매치 0건(신규 소환사)일 때 빈 응답 처리, 0데스 KDA
- [x] **큐 표시명 매핑(§5 확정 — 이번에 같이 고침)**: `QueueNames`(신규, `RIFT_QUEUE_TYPES` 4개만 커버 — 이 화면들에 실제로 노출되는 큐가 이것뿐이라 Data Dragon 전체 큐 목록까지 파싱할 필요 없음) `420→"솔로랭크"`, `440→"자유랭크"`, `400→"일반(드래프트)"`, `430→"일반(블라인드)"`. `MatchSummaryResponse.from()`과 `MatchDetailResponse.from()`에서 `match.getQueueType()` 대신 `QueueNames.displayName(match.getQueueType())` 사용 — DB엔 raw id 그대로 저장(원칙①, 표시 레이어에서만 변환). 템플릿은 그 필드를 그대로 출력하고 있어 수정 불필요
- [x] 테스트: `QueueNames.displayName("420")` 등 4개 매핑 확인, 알 수 없는 id는 원본 그대로 반환(방어적 기본값)

**완료 기준 — 확인됨**: 실제 소환사(id 2536, 크롤러 데이터)로 라이브 호출 → `games=405`, `perChampion` games 합(405)이 `games`와 일치, `overallWinRate≈0.506`. `/matches` 응답의 `queueType`이 "솔로랭크"로 표시됨(라이브 확인, DB-only라 크롤러/개발서버 동시가동 문제 없음). 유닛 테스트 6개(QueueNames 2, ChampionStatsService 4) + 전체 스위트 80개 통과.

### 2. 티어 이력 스냅샷 적재 — 4~5h ✅ 완료

- [x] `TierHistory` 엔티티 신규(PROJECT_PLAN.md §6 스키마: id, summoner_id FK, tier, rank, league_points, recorded_at) — `ddl-auto: update`로 자동 테이블 생성
- [x] `TierHistoryRepository` — `findTopBySummonerIdOrderByRecordedAtDesc`(dedup용) + `findBySummonerIdOrderByRecordedAtAsc`(Task 3용, 미리 같이 정의)
- [x] `SummonerService.fetchAndUpsert()`에 훅 추가 — summoner 저장 직후 해당 summoner의 최신 TIER_HISTORY 행과 tier/rank/leaguePoints 비교, **다르면 INSERT, 같거나 tier가 null(언랭)이면 생략**. `findOrFetch`(신규 fetch 시)와 `refresh`(갱신 버튼) 둘 다 이 private 메서드를 거치므로 한 곳만 고치면 양쪽 다 커버됨
- [x] 크롤러(`CrawlerSummonerService`)는 훅 대상에서 제외 — SummonerService를 거치지 않는 별도 클래스라 자동으로 빠짐, 의도적으로 안 건드림(§5 미결 사항 기본값)
- [x] 테스트: 첫 스냅샷 INSERT, 동일 tier/rank/lp로 재검색 시 INSERT 생략(`verify(repo, never())`), 값 변경 시 INSERT, 언랭 전환 시 INSERT 생략(4개 신규, `SummonerServiceTest.java`)

**완료 기준 — 확인됨**: 실제 소환사(id 1, "Grizzly#KR3")로 라이브 검색 → tier_history 1행 생성(CHALLENGER I 2812LP). 곧바로 [전적 갱신] 호출 → 여전히 1행(같은 값이라 dedup 확인). 값이 바뀌는 케이스는 실제 티어 변동을 기다릴 수 없어 유닛 테스트로 검증. 전체 스위트 84개 통과.

### 3. 티어 이력 조회 API — 2~3h ✅ 완료

- [x] `GET /api/summoners/{summonerId}/tier-history` — `SummonerController`에 추가, Task 2 리포지토리 조회, 시계열 DTO 리스트 응답. 존재하지 않는 summonerId는 404
- [x] **차트 y축용 점수 환산(§5 확정 — 차트로 하기로 함)**: `TierScore`(신규 유틸) — `tierIndex(IRON=0..CHALLENGER=9) * 400 + (division ? (4 - divisionIndex) * 100 : 0) + leaguePoints`. **주의(라이브 확인으로 발견)**: league-v4는 Master+에도 `rank="I"`를 내려줌(null 아님) — apex 판정은 rank가 아니라 **tier 이름**으로 분기(`APEX_TIERS.contains(tier)`), 안 그러면 모든 apex 플레이어가 +300 오차를 받음
- [x] `TierHistoryResponse`에 원본 tier/rank/leaguePoints(툴팁/라벨용) + `score`(차트 y축용) 둘 다 포함
- [x] 테스트: `TierScoreTest` 3개(디비전 우선순위, 티어 우선순위, apex rank="I" 오분류 방지)

**완료 기준 — 확인됨**: 실제 소환사(id 1)로 라이브 호출 → `[{"tier":"CHALLENGER","rank":"I","leaguePoints":2812,"score":6412}]`(9×400+2812, apex라 division 보너스 없음 — 수기 계산과 일치). 존재하지 않는 id는 404. 전체 스위트 87개 통과.

### 4. 화면 — 챔피언 통계 탭 + 티어 이력 탭 — 6~8h ✅ 완료

- [x] `profile.html`에 Bootstrap `nav-tabs` 신규 도입(현재 프로필 화면엔 탭 구조 자체가 없음 — 매치 목록만 단일 섹션) — 매치 목록 / 챔피언 통계 / 티어 이력 3탭. `nav-tabs`의 `data-bs-toggle` 동작에 필요한 `bootstrap.bundle.min.js`(JS+Popper)가 이전엔 프로젝트 어디에도 로드된 적 없어서(`layout.html`엔 CSS만) 이 화면에 신규 추가
- [x] 챔피언 통계 탭: 전체 승률, 최근 폼(W/L 아이콘 나열), 챔피언별 표(ddragon 아이콘 재사용 — Phase 1에 이미 연동됨) — **"N게임 기준" 표기** 완료
- [x] **티어 이력 탭 — 라인 차트(§5 확정)**: `Chart.js` CDN `<script>` 추가(계획과 달리 `layout.html`이 아니라 `profile.html`에서 직접 로드 — 이 화면에서만 쓰여서 다른 페이지까지 끌고 갈 필요 없음). x축 `recordedAt`, y축 Task 3의 `score`(눈금 자체는 상대값이라 숨김), 포인트 툴팁에 원본 tier/rank/LP 표시. 데이터 없으면 빈 상태 문구
- [x] `PageController` 수정 — 프로필 페이지 로드 시 `ChampionStatsService` 호출해 모델에 추가(SSR). 티어 이력은 이미 Task 3에서 검증된 `/api/summoners/{id}/tier-history`를 클라이언트 사이드에서 그대로 fetch(서버 모델에 안 실음 — 화면 전용 API라 중복 조회 불필요)

**완료 기준 — 부분 확인됨**: `/summoners/Grizzly/KR3` 실제 응답(HTTP 200)에 3개 탭 마크업(`data-bs-toggle="tab"`, `championStatsPane`, `tierHistoryPane`, `tierHistoryChart` 캔버스)과 `bootstrap.bundle.min.js`/`chart.umd.min.js` 로드 태그가 모두 존재함을 서버 사이드에서 확인. 같은 소환사(id 1)로 `/api/summoners/1/champion-stats`(games=36, perChampion 14종) · `/api/summoners/1/tier-history`(1건, score=6412) 둘 다 라이브 200 응답 확인 — 화면이 그리는 데이터 자체는 두 API 모두 Task 1/3에서 이미 검증된 값과 일치.
**브라우저 시각 확인 — 완료됨(Playwright)**: claude-in-chrome은 두 세션 모두 이 세션의 개발 서버가 아닌 전혀 다른 서버로 연결돼(툴링 환경 한계) 포기, 대신 로컬 Playwright(webapp-testing 스킬)로 같은 호스트(127.0.0.1:8080, ctx_execute가 실제로 도달했던 경로)에서 헤드리스 크로미움으로 직접 확인. 결과: 챔피언 통계 탭(36게임 기준·승률 69%, 최근 폼 배지, 챔피언별 표 14행) 정상 렌더링, 티어 이력 탭 클릭 후 캔버스에 포인트 1개(현재 이력 1건과 일치, `getImageData`로 6558픽셀 그려짐 확인) 정상 렌더링, 콘솔 에러 0건. 탭이 `display:none`인 비활성 pane 안에 있어 `Chart.js` 초기화 시 캔버스 폭이 0일 수 있다는 우려가 있었으나 실측 결과 정상 렌더링(Chart.js 4의 ResizeObserver가 처리) — 코드 수정 불필요. 스크린샷: `1_matches_tab.png`, `2_champion_stats_tab.png`, `3_tier_history_tab.png`(스크래치패드).

### 5. 테스트 정리 — 1~2h ✅ 완료

- [x] Task 1~4 엣지 케이스 점검 — 이미 다 커버돼 있음(신규 추가 테스트 없음): 매치 0건은 `ChampionStatsServiceTest.stats_noMatches_returnsEmptyResponseWithoutDivideByZero`, 언랭 전환은 `SummonerServiceTest`의 tier-history dedup 테스트들, 챔피언 1종만 플레이는 `stats_groupsByChampionWithWinRateAndAveragedKda`의 leblanc(1게임) 케이스에서 자연스럽게 커버됨
- [x] `./gradlew test --rerun-tasks` 전체 통과 확인 — **87/87 통과**, 실패/에러 0건(캐시 아닌 실제 재실행 결과)

**Phase 3 완료.**

---

## 4. Phase 3 완료 기준 (Definition of Done)

PROJECT_PLAN.md §4 Phase 3 체크리스트 전체 충족 + 아래 확인:

1. [x] 승률/최근 폼/챔피언별 통계가 랭크·드래프트 큐만 집계(ARAM 등 섞인 소환사로 확인) + "N게임 기준" 화면 표기
2. [x] 같은 소환사를 반복 검색해도 TIER_HISTORY가 중복 적재되지 않음, 티어/LP 변화 시에만 적재
3. [x] 프로필 화면에서 챔피언 통계 탭 + 티어 이력 라인 차트 둘 다 실제로 눈으로 확인 가능(Playwright 헤드리스 확인, Task 4 완료 기준 참고)
4. [x] 매치 목록/상세 화면에 큐 타입이 raw id("420") 대신 사람이 읽을 수 있는 라벨("솔로랭크")로 표시됨

### 실측 트래킹

| 항목 | 추정 | 완료일 | 메모 |
|---|---|---|---|
| 1. 통계 집계 서비스 + 큐 표시명 매핑 | 5~6h | 2026-08-06 | |
| 2. 티어 이력 스냅샷 | 4~5h | 2026-08-06 | |
| 3. 티어 이력 API + 점수 환산 | 2~3h | 2026-08-06 | |
| 4. 화면(챔피언 통계 + 티어 이력 차트) | 6~8h | 2026-08-08 | 브라우저 시각 확인은 claude-in-chrome이 이 세션 개발 서버에 도달 못 해 로컬 Playwright로 전환해 확인 |
| 5. 테스트 정리 | 1~2h | 2026-08-08 | 신규 테스트 없음(기존 커버리지로 충분) |
| **합계** | **18~24h** | | PROJECT_PLAN.md §10 추정(22~26h) 범위 안 — 신규 인프라는 없지만 차트 렌더링 + 큐 라벨 매핑이 추가돼 최초 초안(15~21h)보다 올라감 |

---

## 5. 결정 사항 & 미결 사항

### 확정 (이번 세션 결정)

| 항목 | 결정 | 비고 |
|---|---|---|
| 티어 이력 화면 형태 | **차트**(Chart.js, CDN) | Bootstrap과 동일한 jsdelivr CDN 패턴, 신규 빌드 도구 불필요(Task 4). tier가 범주형이라 y축용 점수 환산 필요(Task 3 `TierScore`) |
| 기존 "420" 노출 갭 | **Phase 3에서 고침** | Phase 3 DoD에 포함(§4 4번). Task 1에서 `QueueNames` 매핑 추가, `MatchSummaryResponse`/`MatchDetailResponse` 두 지점만 수정 — 템플릿 변경 불필요 |

### 미결 (grilling 대상 — Phase 2 §5처럼 확정 아님)

| 항목 | 열린 질문 | 기본값(확정 아님) |
|---|---|---|
| champion-stats 응답 구조 | 승률/최근폼/챔피언별을 한 엔드포인트 응답에 다 묶을지, 라우트를 쪼갤지 — PROJECT_PLAN.md §4 라우트 표(352행)엔 엔드포인트 1개만 명시 | 한 응답에 묶음(Task 1 DTO 그대로) |
| 크롤러 → TIER_HISTORY | 크롤러가 수집한 소환사(현재 1만여 명)도 스냅샷 1건씩 넣을지 — PROJECT_PLAN.md 132행이 "적재 무방"이라고만 하지 강제 아님 | 안 함 — 스냅샷 1건은 "이력"이 아니라 값 하나라 제품 가치가 없고, 크롤러는 go-live 후 은퇴(§4 125행) |
