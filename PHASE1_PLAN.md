# Phase 1 상세 작업 계획서 (기본기 / MVP)

> [PROJECT_PLAN.md](./PROJECT_PLAN.md) §4 Phase 1을 실행 단위로 쪼갠 작업 분해서(WBS). 순서·산출물·완료 기준을 명시해 Claude Code 세션에서 항목 단위로 요청할 수 있게 함.
> 예산: **34~38h** (1주차 ~24h + 2주차 10~14h, PROJECT_PLAN.md §10 캘린더 기준)

## 0. 착수 전 확인 사항

- [ ] Riot Developer Portal에서 **Development API Key** 발급 (24h 만료 — 매일 갱신 필요, PROJECT_PLAN.md §11)
- [ ] 로컬 MySQL 실행 방법 확정 — Redis는 Phase 2부터이므로 **Phase 1은 docker-compose로 MySQL만** 띄우면 됨
- [ ] 패키지 루트(GroupId) 확정 — 아래 예시는 `com.lolstats`로 가정, 실제 값으로 치환

## 1. 이 문서 범위에 포함되지 않는 것 (Phase 2 이후)

Phase 1 도중 아래를 미리 만들고 싶은 유혹이 들 수 있는데, 계획서상 명시적으로 Phase 2 항목이므로 **여기서 만들지 않는다**:

- Bucket4j 전역 Rate Limiter, per-IP 제한
- 백그라운드 수집 큐(`@Async`/워커 스레드), `collecting`/`collectedCount` 폴링 응답
- Redis (인기 검색어 ZSet, 쿨다운, 중복 큐잉 방지) — Phase 1의 인기 검색어는 **DB 직접 조회**
- 429 재시도 로직 — Phase 1은 **429 시 부분 결과 표시 후 중단**(실패 허용)
- `[전적 갱신]` 버튼, JWT/로그인, 즐겨찾기

---

## 2. 작업 순서 (의존성 기준)

```
0. 프로젝트 초기 세팅
      │
1. DB 스키마 / JPA 엔티티
      │
2. Riot API 클라이언트 (asia/kr 라우팅)
      │
   ┌──┴───────────────┐
3. 소환사 조회 서비스   5. Data Dragon 연동 (독립적으로 병행 가능)
   (DB 캐시 우선)            │
      │                      │
4. 매치 수집 (동기 최소)      │
      │                      │
6. 티어 엠블럼 정적 리소스 ───┤
      │                      │
7. 자동완성 API               │
      │                      │
8. 검색 입력 검증 (FE/BE)     │
      │                      │
      └──────────┬───────────┘
                  ▼
         9. 화면 3종 (Thymeleaf)
                  │
                  ▼
      10. 테스트 정리 / 누락분 보강
```

Data Dragon 연동(5)은 Riot API·DB와 무관하므로 2번 이후 아무 때나, 심지어 병렬로 진행 가능. 나머지는 순서대로 진행해야 뒤 항목이 앞 항목의 산출물(엔티티, 클라이언트, 서비스)을 그대로 사용할 수 있음.

---

## 3. 상세 작업 항목 (WBS)

### 0. 프로젝트 초기 세팅 — 2~3h

- [x] Spring Initializr로 프로젝트 생성: Spring Web, Spring Data JPA, MySQL Driver, Validation, Thymeleaf, Lombok (Spring Boot 4.1.0 — 결정 사항 참고)
- [x] 패키지 구조 확정 — `domain`/`repository`/`service`/`controller`/`client`/`dto`/`config`로 결정, 실제 디렉터리는 Task 1~2에서 첫 클래스와 함께 생성(빈 패키지 미리 안 만듦)
- [x] `application-dev.yml` / `application-prod.yml` 프로필 분리 (PROJECT_PLAN.md §9.5와 정합 — Phase 1은 dev만 실사용)
- [x] Riot API base URL 2종을 설정값으로 분리: `riot.api.platform-url`(kr), `riot.api.regional-url`(asia) (§4 라우팅 노트)
- [x] Riot API Key를 환경변수/`.env`로 주입 (커밋 금지 — §9.5와 동일 기준을 Phase 1부터 적용, `.env.example`로 문서화)
- [x] Git 저장소 초기화, 첫 커밋 (`cdd4213`)

**완료 기준**: ✅ `./gradlew test`(컨텍스트 로드 테스트)가 로컬 MySQL(docker-compose, 포트 3307 — 호스트 3306이 기존 MySQL 서비스로 사용 중이라 회피)에 연결해 BUILD SUCCESSFUL.

### 1. DB 스키마 / JPA 엔티티 — 2~3h

Phase 1에 필요한 4개 테이블만 우선 구현 (PROJECT_PLAN.md §6 전체 스키마 중 발췌):

- [x] `SUMMONERS` 엔티티 (puuid unique, game_name/tag_line, tier/rank/league_points nullable, wins/losses, updated_at) — `rank`는 MySQL 8 예약어라 `@Column(name = "`rank`")`로 백틱 처리
- [x] `MATCHES` 엔티티 (riot_match_id unique, game_creation, game_duration, queue_type)
- [x] `MATCH_PARTICIPANTS` 엔티티 — **SUMMONERS FK 없음**, puuid를 인덱스 컬럼으로만 저장 (§6 설계 노트 — FK 강제 시 매치 저장마다 껍데기 소환사 10명 생성 문제 방지). items_json/runes_json은 Hibernate `@JdbcTypeCode(SqlTypes.JSON)`로 MySQL 네이티브 JSON 컬럼 매핑(추가 의존성 없음)
- [x] `SEARCH_COUNTS` 엔티티 (summoner_id 1:1 PK, search_count, last_searched_at) — `@OneToOne @MapsId`로 Summoner와 PK 공유
- [x] `MATCH_PARTICIPANTS.puuid`, `SUMMONERS.game_name`에 인덱스 추가 (자동완성 LIKE 검색, 매치 조회용)
- [x] 스키마 생성은 **`spring.jpa.hibernate.ddl-auto=update`**로 진행 (dev 프로필). 배포 시점(§9)에 prod는 `validate`로 전환 예정 — 지금은 별도 마이그레이션 도구 도입 안 함

**완료 기준**: ✅ 엔티티 4개가 로컬 MySQL에 테이블로 생성되고(`SHOW TABLES`/`DESCRIBE`로 확인), Repository 기본 CRUD가 `RepositoryIntegrationTest`(`@DataJpaTest` + `Replace.NONE`, 실제 MySQL 대상)로 확인됨.

> ⚠️ 이 단계에서 결정이 필요한 것: **JPA `ddl-auto` vs 수동 마이그레이션**. 계획서에 도구가 지정돼 있지 않으므로, 시작 전에 정하는 게 좋음.

### 2. Riot API 클라이언트 — 5~6h

- [x] HTTP 클라이언트 설정 — **RestClient** 사용, `RiotApiConfig`에서 `riotPlatformClient`(kr)/`riotRegionalClient`(asia) 빈 2개로 분리
- [x] `account-v1` (asia) — `GET /riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}` → puuid 취득
- [x] `summoner-v4` (kr) — `GET /lol/summoner/v4/summoners/by-puuid/{puuid}` → profileIconId, summonerLevel
- [x] `league-v4` (kr) — **`GET /lol/league/v4/entries/by-puuid/{puuid}`** (by-summoner 아님 — §4 라우팅 노트) → 응답은 큐별 배열 그대로 반환, RANKED_SOLO_5x5 필터링은 Task 3(소환사 조회 서비스) 책임
- [x] `match-v5` ID 목록 (asia) — `GET /lol/match/v5/matches/by-puuid/{puuid}/ids?count=20`
- [x] `match-v5` 상세 (asia) — `GET /lol/match/v5/matches/{matchId}` — `perks`는 `JsonNode`로 원형 보존(룬 서브셋 추출은 Task 4에서)
- [x] Riot 응답 → 내부 DTO 매핑 클래스 분리 (§8 리스크: "Riot 응답 스키마 변경" 대응 — DTO 계층으로 격리). 미사용 필드는 개별 어노테이션 대신 `spring.jackson.deserialization.fail-on-unknown-properties: false` 전역 설정으로 무시
- [x] 최소 예외 처리 — 커스텀 예외 없이 Spring `RestClient`가 던지는 `HttpClientErrorException.NotFound`/`.Unauthorized`/`.Forbidden`/`.TooManyRequests`를 그대로 전파(각각 타입이 분리돼 있어 상위에서 캐치 가능). 429 재시도는 Phase 2
- [x] Mockito로 mock 가능하도록 `RiotApiClient` 인터페이스로 분리 (`RiotApiClientImpl`이 구현)

> ⚠️ **Spring Boot 4.1은 Jackson 3을 씁니다** (`tools.jackson.*`, groupId `tools.jackson.core` — 기존 Jackson 2의 `com.fasterxml.jackson.databind`가 아님). `RiotMatchResponse.perks`가 `tools.jackson.databind.JsonNode`인 이유. 이후 Jackson 관련 코드 작성 시 계속 적용됨.

**완료 기준**: ✅ `RiotApiClientImplTest`(`MockRestServiceServer`, 실제 API 키 불필요)로 5개 메서드 전부 라우팅(asia/kr)·헤더(`X-Riot-Token`)·응답 매핑 확인. ✅ 실제 Development Key + "Hide on bush#KR1"로 account→summoner→league→match ids→match 상세 전체 체인 라이브 curl 검증까지 완료 — DTO 필드명이 실제 Riot 응답과 전부 일치(`riotIdGameName`/`riotIdTagline`/`item0~6`/`perks.statPerks`/`perks.styles` 등).

### 3. 소환사 조회 서비스 (DB 캐시 우선) — 4~5h

- [x] `SummonerService.findOrFetch(gameName, tagLine)`: SUMMONERS에 (game_name, tag_line) 매치 존재 + `updated_at` 만료 전이면 DB 값 반환 — `(game_name, tag_line)`에 unique 제약이 없어(§6) 리포지토리는 `List` 반환, 여러 행이면 `updatedAt` 최신 행을 후보로 사용
- [x] 캐시 미스/만료 시 Riot API 클라이언트 순차 호출 → SUMMONERS upsert
- [x] **닉네임 변경 대응**: 이름으로 히트했어도 만료 후엔 puuid 기준으로 재조회, puuid가 다르면 새 행으로 처리 (§6 닉네임 변경 정책 — 이 로직 없으면 나중에 되짚기 어려움, Phase 1부터 반영 권장)
- [x] 조회 성공 시 `SEARCH_COUNTS` upsert (search_count +1, last_searched_at 갱신)
- [x] TTL(캐시 만료 기준 시간) = **10분**, 설정값(`application.yml`)으로 분리해 추후 조정 가능하게

**완료 기준**: ✅ `SummonerServiceTest`(Mockito, 4개 케이스 — fresh cache hit/expired refetch/unranked null 처리/닉네임 소유자 변경) + 실제 MySQL·Riot API 대상 1회성 라이브 확인(임시 테스트, 커밋 안 함): 첫 호출은 SELECT(이름)→SELECT(puuid)→INSERT 후 Riot 데이터 반환, 두 번째 호출은 SELECT(이름) 한 줄만 찍히고 Riot 호출 없이 캐시값 그대로 반환됨을 Hibernate SQL 로그로 확인.

### 4. 매치 수집 (동기 최소) — 4~5h

- [x] 매치 ID 20개 목록 조회 → DB에 이미 있는 `riot_match_id`는 필터링(원칙 ① — 재요청 금지)
- [x] 없는 매치 중 **5건만**(3~5 범위의 상단) 상세 조회 후 MATCHES/MATCH_PARTICIPANTS 저장
- [x] MATCH_PARTICIPANTS 저장 시 `items_json`/`runes_json`/`spell1_id`/`spell2_id` 함께 저장 (Phase 1 필수 — §6). `queue_type`은 Riot의 원본 `queueId`(정수, 예: `"420"`)를 문자열 그대로 저장 — 랭크/드래프트 분류는 Phase 3에서 이 값 기준으로 처리(지금 임의로 이름 매핑하면 오분류 위험)
- [x] 429 응답 처리: 가져온 만큼만 저장하고 남은 건 조용히 중단(에러로 죽지 않게) — "실패 허용" 명시대로
- [x] `GET /api/summoners/{summonerId}/matches?page=&size=` 엔드포인트 (Phase 1은 `collecting` 필드 없이 DB에 있는 매치만 페이지네이션 반환, 수집 트리거 없음)
- [x] `GET /api/matches/{riotMatchId}` 매치 상세 엔드포인트

> **컨트롤러 보강 (Task 3→4 연결)**: Task 3은 서비스 레이어만 구현하고 컨트롤러를 미뤘는데, 본 Task의 완료 기준 자체가 "소환사 검색 시" 매치 수집을 요구해 트리거 지점이 필요했다. `GET /api/summoners/riot-id/{gameName}/{tagLine}`(§7 스펙과 동일 경로)를 추가해 `SummonerService.findOrFetch` → `MatchService.collectRecentMatches`를 이어 호출하도록 구성. `items`/`runes`는 이미 JSON 텍스트로 저장돼 있어 `@JsonRawValue`로 이중 인코딩 없이 그대로 내려줌.

**완료 기준**: ✅ 실제 서버 기동 + 실 Riot API로 확인. 첫 호출 5건 저장 → 재호출마다 이미 저장된 건 건너뛰고 다음 5건씩 추가(5→10→15→20) → 매치 ID 목록(20개) 전부 채워지면 더 이상 증가하지 않음(DB COUNT로 확인). `/api/matches/{riotMatchId}`도 10명 참가자 전부 정상 반환 확인.

### 5. Data Dragon 연동 — 4~5h

- [x] `versions.json` 조회 → 최신 버전 캐싱 (인메모리, 하루 1회 갱신 — **앱 기동 시 `@PostConstruct` 로드 + 매 조회마다 TTL 체크**하는 방식 채택, 별도 스케줄러(`@EnableScheduling`) 도입 안 함)
- [x] `champion.json`, `item.json`, `runesReforged.json` (ko_KR) 매핑 데이터 앱 캐싱 (인메모리 Map, 버전 갱신 시 함께 갱신). **`summoner.json`(스펠)도 함께 캐싱** — Task 5 체크리스트엔 없었지만 §4 Phase 1 요건("스펠 아이콘 표시")과 매치 데이터의 `spell1_id`/`spell2_id`를 실제 아이콘으로 바꾸려면 champion.json과 동일한 이유(내부 이름과 숫자 ID가 분리돼 있음)로 반드시 필요 — 정적 ID→이름 테이블을 손으로 하드코딩하는 대신 동일 패턴으로 캐싱
- [x] 이미지 URL 헬퍼: 챔피언/아이템/스펠/프로필아이콘/룬 5종 URL 조합 함수 (§4 Data Dragon 설계 노트의 URL 패턴 그대로 — 룬만 버전 없는 `/cdn/img/` 경로, 나머지는 `/cdn/{version}/img/...`)
- [x] `championId`(int) → 챔피언 이름/이미지 변환. `getChampion`/`getItem`/`getSpell`/`getRuneIconUrl`/`getProfileIconUrl`로 노출(Thymeleaf 화면은 Task 9에서 소비)

**완료 기준**: ✅ Mockito 7케이스(챔피언/아이템/스펠/룬 조회, 미존재 ID는 `Optional.empty()`, TTL 이내 재조회 시 재호출 안 함) + 실제 Data Dragon CDN 대상 라이브 확인(임시 테스트, 커밋 안 함): `championId 103` → `ChampionInfo[id=103, name=아리, imageUrl=.../16.14.1/img/champion/Ahri.png]`, 버전은 `versions.json`에서 동적으로 가져온 `16.14.1`(하드코딩 없음). 아이템/스펠/룬/프로필아이콘 URL도 전부 실제 CDN에서 200 확인.

### 6. 티어 엠블럼 정적 리소스 — 0.5~1h

- [ ] Riot 개발자 포털 "Ranked Emblems" 자산 다운로드 → `src/main/resources/static/images/tier-emblems/`
- [ ] tier 문자열(IRON~CHALLENGER) → 이미지 파일명 매핑 함수/상수

### 7. 자동완성 API — 2~3h

- [ ] `GET /api/summoners/autocomplete?query=&limit=` — `game_name LIKE 'query%'` 검색
- [ ] `ORDER BY last_searched_at DESC` + **puuid 기준 dedupe** (§4 설계 노트 — 동일 이름 옛 주인/새 주인 중복 노출 방지)
- [ ] `GET /api/summoners/popular?limit=` — Phase 1은 Redis 없이 **SEARCH_COUNTS를 DB에서 직접 정렬 조회**

**완료 기준**: 과거 검색된 소환사 이름 앞부분을 입력하면 후보가 뜨고, 동일 이름 중복이 puuid 기준으로 걸러짐.

### 8. 검색 입력 검증 (FE/BE) — 1~2h

- [ ] BE: 검색 요청 DTO에 `@NotBlank`, `@Size(min=3, max=16)`(게임 이름), `@Size(min=3, max=5)`(태그라인) 적용
- [ ] `@ControllerAdvice`로 검증 실패 응답 포맷 통일 (§4 Validation 정책 — 이후 Phase에서도 재사용되는 전역 예외 처리이므로 Phase 1에서 골격을 잡아두면 이득)
- [ ] FE: HTML5 `maxlength`/`pattern` + 간단한 JS 실시간 피드백

### 9. 화면 3종 (Thymeleaf + Bootstrap 5) — 6~8h

- [ ] 메인 화면: 검색창, 최근 검색어(로컬스토리지), 인기 검색어(§7 popular API 연동)
- [ ] 프로필 화면: 티어 엠블럼/레벨/승률 요약(league-v4 값), 최근 매치 리스트(아이콘 기반 — 챔피언 초상화, 아이템 6칸, 룬, 스펠)
- [ ] 매치 상세 화면: 양 팀 10명 KDA/아이템/룬 (MATCH_PARTICIPANTS 그대로 렌더링, 참가자 10명 중 검색 이력 없는 8~9명은 닉네임 스냅샷만 표시)
- [ ] Bootstrap 5 CDN 연결, 카드/네비바/테이블 기본 컴포넌트로 구성 (커스텀 CSS 최소화 — §3 화면 기술 선택 이유)

**완료 기준**: 브라우저에서 닉네임 검색 → 프로필 → 매치 상세까지 클릭으로 이동 가능.

### 10. 테스트 정리 — 항목별 병행 + 마무리 1~2h

> §4 테스트 정책: 각 항목 구현과 **같은 작업 단위**에서 Service 테스트를 함께 작성 — 별도 "테스트 Phase"로 미루지 않음. 아래는 항목 3~7 작업 중 자연히 쌓이는 테스트의 목록이며, 마지막에 빈틈만 정리.

- [ ] `SummonerService` — 캐시 히트/미스/만료, 닉네임 변경 케이스 (Riot 클라이언트 Mockito mock)
- [ ] 매치 수집 서비스 — 이미 있는 매치 skip, 429 부분 실패 케이스
- [ ] 자동완성 — dedupe 로직
- [ ] Data Dragon 매핑 — 버전 갱신 트리거 조건

---

## 4. Phase 1 완료 기준 (Definition of Done)

PROJECT_PLAN.md §4 Phase 1 체크리스트 전체 충족 + 아래 3가지 확인:

1. 신규 닉네임 검색 → 프로필 → 매치 상세까지 실제 브라우저에서 동작
2. 같은 소환사 재검색 시 Riot API 호출이 (로그 기준) 발생하지 않음 — DB 캐시가 실제로 동작하는지가 Phase 1의 핵심 검증 포인트
3. 패치 버전이 바뀌어도 아이콘이 깨지지 않는 구조(버전 하드코딩 없음)임을 코드 리뷰로 확인

## 5. 결정 사항 (확정)

| 항목 | 결정 | 비고 |
|---|---|---|
| DB 스키마 관리 | `ddl-auto: update` (dev) | 배포 시점(§9) prod는 `validate`로 전환, 마이그레이션 도구는 미도입 |
| HTTP 클라이언트 | RestClient | Phase 2 비동기도 블로킹 RestClient를 별도 스레드에서 감싸는 방식으로 재사용 |
| SUMMONERS 캐시 TTL | 10분 | `application.yml` 설정값으로 분리, 필요 시 조정 |
| Spring Boot 버전 | **4.1.x** (계획서 원안 3.x에서 변경) | 2026-07-28 착수 시점 start.spring.io가 `bootVersion compatibility range >=4.0.0`으로 3.x 생성을 거부함(3.x 사실상 EOL). 실서비스 배포(§9) 대상이라 EOL 프레임워크 대신 최신 안정판 채택. springdoc-openapi/bucket4j 등 Phase 2~5 라이브러리의 4.x 호환은 해당 Phase 착수 시 재확인 필요 |
