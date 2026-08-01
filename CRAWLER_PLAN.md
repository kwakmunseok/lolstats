# 시드 크롤러 작업 계획서

> [PROJECT_PLAN.md](./PROJECT_PLAN.md) §4 "시드 크롤러"(grilling 2026-07-30 확정) + §10 재작성 캘린더(1주차 잔여, 07/30–08/03, 4~6h)를 실행 단위로 쪼갠 작업 분해서. Phase1/Phase2 문서와 같은 형식이지만 분량은 의도적으로 축소했다 — 이 도구는 **원샷 업로드 후 은퇴하는 로컬 전용 부트스트랩**(§4 원문)이라 무거운 WBS가 어울리지 않는다.

## 0. 착수 전 확인 사항

- [x] **league-v4 엔드포인트 정확한 모양을 Riot 공식 문서로 재확인** — 기억에 의존해 하드코딩 금지(PHASE2_PLAN.md §0의 Bucket4j 버전 확인과 같은 이유). **확인 결과(착수 시 조사, WebSearch + 최신 커뮤니티 클라이언트 라이브러리 필드 정의 교차 확인 — developer.riotgames.com 직접 fetch는 로컬 도구 장애로 불가해 2차 소스로 대체)**:
  - 챌린저/그마/마스터(apex 3티어)는 예상대로 `entries/{queue}/{tier}/{division}?page=`와 **별도 엔드포인트** 확정 — `challengerleagues`/`grandmasterleagues`/`masterleagues` **by-queue**, 페이지 없이 전원 한 번에 반환(`LeagueListDTO { tier, entries: [LeagueItemDTO] }` — `tier`는 래퍼에만 있고 항목별로는 없음)
  - 다이아~아이언은 `entries/{queue}/{tier}/{division}?page=N` 페이징(division I~IV) 확정, 응답에 항목별 `tier` 필드 포함(래퍼 없는 flat list)
  - 두 응답 모두 `puuid` 필드 확인됨(레거시 `summonerId`는 유지되나 곧 제거 대상 — Riot DevRel이 puuid 기반 엔드포인트로 이전 안내 중)
  - **원문에 없던 추가 발견**: 현재 티어 목록에는 **PLATINUM과 DIAMOND 사이에 EMERALD**가 있음(2023 시즌 추가, PROJECT_PLAN.md §4 원문 작성 시점엔 반영 안 됨) — 하강 순서에 포함해 구현(SeedCrawlerRunner)
- [x] `SummonerService`의 기존 저장 경로 중 크롤러가 재사용할 지점이 `recordSearch()`(SEARCH_COUNTS 증가·`ZINCRBY`)를 안 타는지 코드로 확인 — 조건②(§4) 위반 방지. **(grilling 2026-07-30 확정) 해소**: `fetchAndUpsert()`는 private이고 항상 계정+소환사+리그 3회 호출이라 크롤러의 "신규만 호출/기존은 무호출" 패턴과 안 맞음 → 크롤러는 `SummonerService`를 아예 호출하지 않고 `SummonerRepository`를 직접 쓰는 전용 upsert 메서드를 새로 둔다(§3 Task 1). `recordSearch()`는 애초에 호출 경로에 없으므로 구조적으로 접촉 불가
- [x] `MatchService.planCollection`/`collectMatches`가 SEARCH_COUNTS/Redis 인기검색어에 관여하지 않는지 확인(설계상 소환사 레이어 관심사가 아니라 안전할 것으로 보이나, 재사용 전 확인) — **코드 확인 완료**: 둘 다 Redis/SearchCount에 접촉하지 않음(Redis 접촉은 `MatchCollectionQueue`뿐이고 크롤러는 이를 우회)
- [x] SUMMONERS 컬럼 추가 방식 확인 — Flyway 마이그레이션 파일이 있으면 그 방식대로, 없으면 기존 `ddl-auto` 방식 그대로 추가. **확인 결과**: Flyway 마이그레이션 없음 → dev는 `ddl-auto: update`로 로컬에 자동 반영되지만, **prod는 `ddl-auto: validate`라 자동 생성 안 됨** — EC2 배포 체크리스트에 `ALTER TABLE summoners ADD COLUMN crawler_backfilled_at datetime NULL` 수동 실행 추가 필요(§5)

## 0.1 진행 현황

Task 1~4 구현 완료(TDD, `feature/seed-crawler` 브랜치). Task 5(업로드 슬라이스 export)는 계획대로 후순위 보류 — 5주차 go-live 직전 작성.

재개 시 확인할 것: `./gradlew test`로 회귀 확인 → 로컬 `docker compose up -d` → `RIOT_API_KEY` 등 `.env` 로드 → `./gradlew bootRun --args='--spring.profiles.active=dev,crawler'`로 가동.

## 1. 이 문서 범위에 포함되지 않는 것

- **스노볼 확장**(매치 참가자 10인의 puuid로 시드 추가 확보) — PROJECT_PLAN.md §4 원문에도 "가능"이라고만 되어 있을 뿐 필수가 아님. Master+ 슬라이스 확보라는 MVP 목표를 넘는 확장이라 1차 빌드 범위 밖(필요해지면 별도 Task로 추가)
- **EC2/상시 서버에 크롤러 배포** — (A) 로컬 전용으로 이미 확정(§4), 배포 트랙과 무관
- **실제 업로드 실행**(go-live 직전 `TRUNCATE` + `mysqldump` 적재) — 스크립트 준비까지만 이 문서 범위, 실행 시점은 §9.6 캘린더(5주차)
- 다른 전적 사이트(op.gg 등) 스크래핑 — 절대 금지(§4 명시)
- Phase 3/4/5 기능 자체 — 크롤러는 그 표본을 미리 쌓아두는 도구일 뿐

---

## 2. 작업 순서

```
0. 확인 사항 (엔드포인트 모양, recordSearch 비접촉)
      │
1. SUMMONERS 시드 upsert (레이어①) + crawler_backfilled_at 컬럼
      │
2. 매치 백필 연동 (레이어②③ — MatchService 재사용)
      │
3. 티어 하강 루프 + 실행 진입점 (CommandLineRunner)
      │
4. 테스트 (TDD)
      │
5. (선택·후순위) 업로드 슬라이스 export 스크립트 초안
```

---

## 3. 상세 작업 항목 (WBS)

### 1. SUMMONERS 시드 upsert — 1.5~2h ✅ 완료

- [x] SUMMONERS에 `crawler_backfilled_at`(datetime, nullable) 컬럼 추가 — "이 puuid의 매치 백필이 끝났다"는 명시적 완료 마커. **"로컬 매치 수 ≥ 20"으로 대체하지 않음** — 시즌 게임 수가 20 미만인 소환사는 이 조건이 영원히 안 걸려서 재시작할 때마다 매치 ID 목록을 다시 조회하게 되고(레이어②), 이런 소환사가 누적되면 재시작마다 낭비되는 호출이 "체크포인트가 아예 없는 것"과 비슷한 규모가 됨 — 명시적 마커로 이 구멍을 막는다
- [x] `SummonerRepository`를 직접 쓰는 크롤러 전용 upsert 메서드 신규 작성(`SummonerService.fetchAndUpsert()` 재사용 아님 — §0/§5, private + 항상 3-call이라 아래 분기 패턴과 안 맞음). 신규 puuid만 account-v1(이름) + summoner-v4(아이콘/레벨) 호출. 이미 DB에 있는 puuid는 Riot 재호출 없이 tier/rank/leaguePoints**+wins/losses** 4컬럼만 갱신(엔트리 응답에 이미 포함돼 있어 공짜 — PROJECT_PLAN.md §4 "소환사당 league-v4 재호출 불필요"). **이 부분 갱신 경로는 `updatedAt`을 건드리지 않는다** — gameName/tagLine/profileIconId/summonerLevel은 그대로 두므로 `updatedAt`을 찍으면 `isFresh()`가 이 필드들을 실제로는 오래됐는데도 "신선"으로 오판해 다음 실사용자 검색이 갱신을 건너뜀(§5)
- [x] 저장 경로가 SEARCH_COUNTS/`ZINCRBY`를 안 타는 것 재확인(§0 항목과 동일) — 위 upsert가 `SummonerService`를 아예 호출하지 않으므로 구조적으로 해소

**완료 기준**: 신규 puuid 10개 정도로 유닛 테스트 — 신규 puuid는 계정+소환사 API 호출이 발생하고, 기존 puuid는 tier/rank/lp/wins/losses만 갱신되며 Riot 클라이언트 호출이 0회인 것을 Mockito `verify`로 확인. 기존 puuid 갱신 케이스에서 `updatedAt`이 변하지 않는 것도 함께 검증. **→ `CrawlerSummonerServiceTest`(2 tests) 충족**

### 2. 매치 백필 연동 — 1~1.5h ✅ 완료

- [x] 기존 `MatchService.planCollection(puuid)` + `collectMatches(matchIds, afterEachSave)`를 재사용(Phase 2에서 이미 구현·검증됨 — 크롤러 전용 백필 로직을 새로 안 짬). **단, `collectMatches`는 완료/부분완료를 구분할 수 있도록 반환값을 추가하는 소폭 시그니처 확장 필요**(§5) — 현재는 `void`라 429로 루프가 중간에 `break`돼도 호출자가 알 방법이 없음
- [x] `MatchCollectionQueue`(Redis `collecting:` 플래그 + 백그라운드 워커)는 **의도적으로 우회**하고 `MatchService`를 직접 호출 — 크롤러 자신이 유일한 워커(단일 스레드 순차 실행)이고, 조건①(로컬 개발 중 크롤러는 항상 꺼져 있음)에 의해 실사용자 검색 큐와 동시 실행될 일이 없으므로 Redis 좌표가 불필요
- [x] `collectMatches`가 **`missingMatchIds` 전부를 실제로 저장했을 때만**(=429로 중단되지 않았을 때만) 해당 puuid의 `crawler_backfilled_at` 갱신. **(grilling 2026-07-30 확정) 부분 백필 상태에서 마킹 금지** — 크롤러는 전역 한도에 상시 붙어 도는 구조라 429 중단은 희귀 케이스가 아니라 흔한 종료 조건. 완료 마킹 후엔 Task 3 DoD("이미 backfilled된 puuid는 재조회 안 함")에 의해 영구히 재시도가 안 되므로, 여기서 오판하면 나머지 매치가 다시는 안 채워짐(§5)

**완료 기준**: 유닛 테스트로 "이미 `crawler_backfilled_at`이 찍힌 puuid는 `planCollection`/`collectMatches` 자체가 호출 안 됨"을 검증(스킵 로직). **추가**: `collectMatches`가 429로 일부만 저장하고 중단된 경우 `crawler_backfilled_at`이 갱신되지 않는 것을 검증. **→ `CrawlerMatchBackfillServiceTest`(3 tests) 충족**

### 3. 티어 하강 루프 + 실행 진입점 — 1~1.5h ✅ 완료

- [x] `@Profile("crawler")` `CommandLineRunner` 신규 추가 — `spring.main.web-application-type=none`으로 웹서버 없이 배치처럼 동작(HTTP API 불필요). 기존 `RiotApiClientImpl`/Bucket4j 빈/JPA 리포지토리 그대로 재사용(새 클라이언트 안 만듦)
- [x] 실행 커맨드: `./gradlew bootRun --args='--spring.profiles.active=dev,crawler'` — `dev` 프로필로 로컬 MySQL/Redis 설정을 그대로 물려받고 `crawler` 프로필로 러너만 켬
- [x] 순회 순서: 챌린저 → 그마 → 마스터(각 1회 호출, 전원 반환) → 다이아 → **에메랄드**(§0 조사 결과 추가) → 플래티넘 → 골드 → 실버 → 브론즈 → 아이언, 각 I~IV(디비전 × 페이지, 빈 응답이 종료 조건) — §0에서 확인한 실제 엔드포인트 모양대로 구현
- [x] **아이언 IV 마지막 페이지(빈 응답) 도달 시 `CommandLineRunner`는 정상 리턴하여 프로세스 종료(재루프 없음)**. **(grilling 2026-07-30 확정)** — DoD의 "무인으로 수 시간~24h 가동"은 상한이지 보장이 아니다. 인구가 적은 상위 티어만 순회하는 초기 실행은 24h보다 훨씬 일찍 끝날 수 있고, 그 경우 프로세스는 그만큼 일찍 멈춘다(다시 돌리려면 수동 재기동 — 체크포인트 없음 결정과 정합)
- [x] **체크포인트 테이블 없음(의도적)** — 티어/디비전/페이지 진행 위치를 별도 저장하지 않고, 매 실행마다 챌린저부터 다시 하강한다. 레이어①은 이미 아는 puuid에 대해 Riot 재호출이 사실상 0이라(Task 1) 재하강 비용이 낮고, 레이어②③은 `crawler_backfilled_at`로 스킵된다 — 정교한 재개 로직 없이도 재시작이 안전(ponytail: 상태를 최소화)
- [x] 예외 처리: 개별 puuid/페이지 처리 중 예외가 나도 루프 전체가 죽지 않고 다음 항목으로 계속(`MatchCollectionQueue.process()`의 `catch(Exception e)` 안전망과 같은 원칙 — §3 Task 3, PHASE2_PLAN.md). **단, 401/403(Dev Key 만료·거부)은 이 "계속 진행" 대상에서 제외** — `MatchCollectionQueue`와 동일하게 감지 즉시 루프 전체를 중단한다(§5). 죽은 키로 계속 진행하면 24h 무인 가동 동안 헛호출만 반복되며 아무도 눈치채지 못함
- [x] 조건①(수동 일시정지) — 앱 내부에 pause 플래그·스케줄러를 두지 않는다. **프로세스를 안 띄우는 것 자체가 일시정지**다. 개발 서버(dev bootRun)와 크롤러(crawler 프로필)는 **별도 JVM 프로세스라 동시에 띄우면 Bucket4j 버킷이 프로세스별로 따로 생겨 Riot 전역 한도를 사실상 중복 소진**하게 됨 — 이래서 동시 실행 금지가 단순한 권장이 아니라 필수 운영 규칙
- [x] 조건②(SEARCH_COUNTS/ZSet 불가침) — Task 1/2에서 이미 보장, 여기선 통합 확인만

**완료 기준**: 로컬에서 몇 분간 실제 가동 → SUMMONERS/MATCHES/MATCH_PARTICIPANTS 행 수가 실제로 느는 것 라이브 확인, `search_rank` ZSet과 SEARCH_COUNTS 값이 실행 전후로 그대로인 것 확인. **→ 라이브 가동 결과는 §0.1/커밋 로그 참고**

### 4. 테스트 — 1h ✅ 완료

- [x] 티어 하강 순서(challenger→grandmaster→master→diamond→emerald→…→iron) 유닛 테스트
- [x] 신규/기존 puuid 분기(Task 1), backfilled 스킵·부분완료 미마킹(Task 2)은 각 Task에서 이미 커버 — 여기선 전체 실행 흐름 통합 확인만
- [x] 401/403 발생 시 루프 전체가 중단되는 것, 아이언 IV 끝(빈 응답) 도달 시 정상 종료(리턴)되는 것 유닛 테스트

**완료 기준**: `./gradlew test` 기존 테스트 회귀 없이 통과 + 신규 테스트 통과. **→ 74/74 통과 확인(RepositoryIntegrationTest 포함, 실제 로컬 MySQL/Redis 대상)**

### 5. (선택·후순위) 업로드 슬라이스 export 스크립트 — 0.5h

- [ ] `mysqldump ... --where="tier IN ('CHALLENGER','GRANDMASTER','MASTER')"` 형태 스크립트 초안 — 실행은 go-live 직전(§9.6, 5주차). 지금 당장 필요하지 않으면 이번 빌드에서 생략하고 5주차에 작성해도 무방(문서에는 위치만 표시해 둠)

---

## 4. 완료 기준 (Definition of Done)

1. 크롤러가 로컬에서 무인으로 장시간(수 시간~24h **상한**) 가동 가능 — 수동 개입 없이 티어를 계속 하강하고, 개별 항목에서 예외가 나도 죽지 않고 계속됨(단 401/403은 예외 — 즉시 전체 중단, §3 Task 3). 하강이 24h 전에 끝나면(아이언 IV 끝) 그만큼 일찍 프로세스가 종료되는 것도 정상 동작
2. SUMMONERS/MATCHES/MATCH_PARTICIPANTS에 데이터가 실제로 쌓임(라이브 확인)
3. SEARCH_COUNTS/Redis `search_rank`는 크롤러 실행으로 값이 변하지 않음(조건②)
4. 재시작해도 이미 backfill된 puuid는 Riot 재호출 없이 건너뜀(로그로 확인)
5. "개발 서버와 크롤러를 동시에 띄우지 않는다"는 운영 규칙이 이 문서에 명시적으로 기록됨(위 §3 Task 3)

---

## 5. 결정 사항 (확정)

| 항목 | 결정 | 비고 |
|---|---|---|
| 실행 형태 | 기존 Spring Boot 앱의 `@Profile("crawler")` `CommandLineRunner` (별도 프로젝트/모듈 아님) | `RiotApiClientImpl`/Bucket4j/JPA 재사용 — 새로 안 만듦 |
| 웹서버 | `spring.main.web-application-type=none` | 배치 성격이라 HTTP 불필요 |
| 매치 백필 로직 | `MatchService.planCollection`/`collectMatches` 재사용 — `collectMatches`는 완료/부분완료 반환값만 소폭 확장 | Phase 2에서 이미 만들고 검증됨 — 재작성은 안 하지만 완전한 "그대로"는 아님(아래 "collectMatches 완료 판정" 행 참고) |
| `MatchCollectionQueue`(Redis 큐) | 우회하고 직접 호출 | 크롤러가 유일한 워커라 Redis 좌표 불필요(조건①로 동시 실행 자체가 없음) |
| 백필 완료 마커 | `SUMMONERS.crawler_backfilled_at`(nullable datetime) | "로컬 매치 수 ≥ 20" 방식은 20게임 미만 소환사에서 영원히 안 걸려 재시작마다 낭비됨 — 명시적 마커로 대체 |
| 체크포인트(티어/페이지 진행 위치) | 없음 — 매 실행 챌린저부터 재하강 | 레이어①은 기존 puuid에 재호출이 없어 공짜에 가깝고, 레이어②③은 backfilled 마커로 스킵됨 — 정교한 재개 로직 불필요 |
| 일시정지 방식(조건①) | 프로세스 자체를 안 띄움 | 앱 내 pause 플래그·스케줄러 없음. 개발 서버와 동시 가동 금지(Bucket4j 버킷이 프로세스별로 따로 생겨 전역 한도 중복 소진) |
| 스노볼 확장 | 1차 빌드 범위 밖 | 매치 참가자 puuid로 확장은 선택 사항(§4 원문) — 필요해지면 별도 Task |
| 업로드 스크립트 실행 시점 | 5주차 go-live 직전(§9.6) | 스크립트 초안만 이번에 준비, 실행은 나중 |
| **collectMatches 완료 판정**(grilling 2026-07-30) | `void` → 저장 건수/완료 여부를 알 수 있게 반환값 추가. `missingMatchIds` 전부 저장했을 때만 `crawler_backfilled_at` 갱신 | 429 중단은 크롤러에서 흔한 종료 조건인데, "부분 백필"을 "완료"로 마킹하면 backfilled 스킵 로직 때문에 영구히 못 채움 |
| **레이어① upsert 구현 위치**(grilling 2026-07-30) | `SummonerService` 재사용 안 함 — 크롤러 전용 신규 upsert 메서드(`SummonerRepository` 직접 호출) | `fetchAndUpsert()`는 private + 항상 3-call이라 "신규만 호출/기존 무호출" 분기와 안 맞음 |
| **기존 puuid 갱신 범위**(grilling 2026-07-30) | tier/rank/lp**+wins/losses**. `updatedAt`은 갱신 안 함 | wins/losses도 entries 응답에 공짜로 포함. `updatedAt`을 찍으면 안 건드린 name/icon/level이 TTL상 "신선"으로 위장돼 실사용자 갱신이 막힘 |
| **`crawler_backfilled_at` 컬럼의 prod 반영**(grilling 2026-07-30) | 배포 체크리스트에 수동 `ALTER TABLE` 추가(§0) | prod는 `ddl-auto: validate` + Flyway 미도입이라 자동 생성 안 됨 |
| **401/403(Dev Key 만료) 처리**(grilling 2026-07-30) | "개별 예외는 계속 진행" 대상에서 제외 — 즉시 루프 전체 중단 | `MatchCollectionQueue`와 동일 패턴. 죽은 키로 계속 진행하면 24h 동안 헛호출만 반복 |
| **하강 완료 시 동작**(grilling 2026-07-30) | 아이언 IV 끝(빈 응답) 도달 시 `CommandLineRunner` 리턴 → 프로세스 종료(재루프 없음) | "무인 24h 가동"은 상한이지 보장 아님 — 인구 적은 상위 티어만 도는 초기 실행은 훨씬 일찍 끝날 수 있음 |
