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

- [ ] `SUMMONERS` 엔티티 (puuid unique, game_name/tag_line, tier/rank/league_points nullable, wins/losses, updated_at)
- [ ] `MATCHES` 엔티티 (riot_match_id unique, game_creation, game_duration, queue_type)
- [ ] `MATCH_PARTICIPANTS` 엔티티 — **SUMMONERS FK 없음**, puuid를 인덱스 컬럼으로만 저장 (§6 설계 노트 — FK 강제 시 매치 저장마다 껍데기 소환사 10명 생성 문제 방지)
- [ ] `SEARCH_COUNTS` 엔티티 (summoner_id 1:1 PK, search_count, last_searched_at)
- [ ] `MATCH_PARTICIPANTS.puuid`, `SUMMONERS.game_name`에 인덱스 추가 (자동완성 LIKE 검색, 매치 조회용)
- [ ] 스키마 생성은 **`spring.jpa.hibernate.ddl-auto=update`**로 진행 (dev 프로필). 배포 시점(§9)에 prod는 `validate`로 전환 예정 — 지금은 별도 마이그레이션 도구 도입 안 함

**완료 기준**: 엔티티 4개가 로컬 MySQL에 테이블로 생성되고, Repository 기본 CRUD가 테스트로 동작 확인됨.

> ⚠️ 이 단계에서 결정이 필요한 것: **JPA `ddl-auto` vs 수동 마이그레이션**. 계획서에 도구가 지정돼 있지 않으므로, 시작 전에 정하는 게 좋음.

### 2. Riot API 클라이언트 — 5~6h

- [ ] HTTP 클라이언트 설정 — **RestClient** 사용 (동기 호출, Phase 2의 `@Async`/`CompletableFuture`도 블로킹 RestClient를 별도 스레드에서 감싸는 방식으로 그대로 재사용)
- [ ] `account-v1` (asia) — `GET /riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}` → puuid 취득
- [ ] `summoner-v4` (kr) — `GET /lol/summoner/v4/summoners/by-puuid/{puuid}` → profileIconId, summonerLevel
- [ ] `league-v4` (kr) — **`GET /lol/league/v4/entries/by-puuid/{puuid}`** (by-summoner 아님 — §4 라우팅 노트) → RANKED_SOLO_5x5 항목만 필터링해 tier/rank/LP/wins/losses 추출
- [ ] `match-v5` ID 목록 (asia) — `GET /lol/match/v5/matches/by-puuid/{puuid}/ids?count=20`
- [ ] `match-v5` 상세 (asia) — `GET /lol/match/v5/matches/{matchId}`
- [ ] Riot 응답 → 내부 DTO 매핑 클래스 분리 (§8 리스크: "Riot 응답 스키마 변경" 대응 — DTO 계층으로 격리)
- [ ] 최소 예외 처리: 404(찾을 수 없음), 401/403(키 만료·오류) 구분 — 429 재시도는 Phase 2, 여기선 예외를 던지고 상위에서 처리
- [ ] Mockito로 mock 가능하도록 클라이언트를 인터페이스로 분리

**완료 기준**: 실제 소환사 닉네임으로 account→summoner→league→match ID→match 상세 순 호출이 로컬에서 성공하고, 각 호출의 라우팅(asia/kr)이 맞게 나감을 확인.

### 3. 소환사 조회 서비스 (DB 캐시 우선) — 4~5h

- [ ] `SummonerService.findOrFetch(gameName, tagLine)`: SUMMONERS에 (game_name, tag_line) 매치 존재 + `updated_at` 만료 전이면 DB 값 반환
- [ ] 캐시 미스/만료 시 Riot API 클라이언트 순차 호출 → SUMMONERS upsert
- [ ] **닉네임 변경 대응**: 이름으로 히트했어도 만료 후엔 puuid 기준으로 재조회, puuid가 다르면 새 행으로 처리 (§6 닉네임 변경 정책 — 이 로직 없으면 나중에 되짚기 어려움, Phase 1부터 반영 권장)
- [ ] 조회 성공 시 `SEARCH_COUNTS` upsert (search_count +1, last_searched_at 갱신)
- [ ] TTL(캐시 만료 기준 시간) = **10분**, 설정값(`application.yml`)으로 분리해 추후 조정 가능하게

**완료 기준**: 같은 소환사를 연속 조회 시 두 번째 호출부터는 Riot API를 타지 않고 DB에서만 응답(로그로 확인).

### 4. 매치 수집 (동기 최소) — 4~5h

- [ ] 매치 ID 20개 목록 조회 → DB에 이미 있는 `riot_match_id`는 필터링(원칙 ① — 재요청 금지)
- [ ] 없는 매치 중 **3~5건만** 상세 조회 후 MATCHES/MATCH_PARTICIPANTS 저장
- [ ] MATCH_PARTICIPANTS 저장 시 `items_json`/`runes_json`/`spell1_id`/`spell2_id` 함께 저장 (Phase 1 필수 — §6)
- [ ] 429 응답 처리: 가져온 만큼만 저장하고 남은 건 조용히 중단(에러로 죽지 않게) — "실패 허용" 명시대로
- [ ] `GET /api/summoners/{summonerId}/matches?page=&size=` 엔드포인트 (Phase 1은 `collecting` 필드 없이 DB에 있는 매치만 페이지네이션 반환)
- [ ] `GET /api/matches/{riotMatchId}` 매치 상세 엔드포인트

**완료 기준**: 신규 소환사 검색 시 매치 3~5건이 저장되고, 같은 소환사 재조회 시 이미 저장된 매치는 다시 Riot API로 안 감을 로그로 확인.

### 5. Data Dragon 연동 — 4~5h

- [ ] `versions.json` 조회 → 최신 버전 캐싱 (인메모리, 하루 1회 갱신 — 스케줄 트리거 or 앱 기동 시 로드 + TTL 체크)
- [ ] `champion.json`, `item.json`, `runesReforged.json` (ko_KR) 매핑 데이터 앱 캐싱 (인메모리 Map, 버전 갱신 시 함께 갱신)
- [ ] 이미지 URL 헬퍼: 챔피언/아이템/스펠/프로필아이콘/룬 5종 URL 조합 함수 (§4 Data Dragon 설계 노트의 URL 패턴 그대로)
- [ ] `championId`(int) → 챔피언 이름/이미지 변환, 화면에서 사용할 수 있는 형태로 Thymeleaf에 노출

**완료 기준**: `championId: 103`을 넣으면 "아리" 한글명과 정확한 이미지 URL이 반환됨. 버전 값이 하드코딩되어 있지 않음.

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
