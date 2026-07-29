# Phase 2 코드 리뷰 가이드

> Phase 2(Task 0~8) 완료 후 직접 코드를 훑어볼 때 참고하는 순서. 처음부터 끝까지 다 읽으라는 게 아니라, "검색 요청 하나가 흘러가는 순서"대로 따라가면 왜 이렇게 짰는지가 가장 잘 보이도록 구성함.
>
> 작성 시점: 2026-07-29 (Phase 2 완료 직후). 이후 코드가 바뀌면 줄 번호는 어긋날 수 있음 — 메서드/클래스 이름으로 찾을 것.

---

## 0. 먼저 큰 그림 (5분)

- `PROJECT_PLAN.md` §4 Phase 2 원문 상단 "아키텍처 원칙 4가지" — 이번 Phase가 왜 이렇게 생겼는지의 근거
- `PHASE2_PLAN.md` — Task별 "라이브 검증 중 발견한 버그" 항목만 먼저 훑기(코드 없이 텍스트만 읽어도 이해되게 써둠)

---

## 1. 검색 요청 하나를 따라가며 읽기

`GET /api/summoners/riot-id/{gameName}/{tagLine}` 요청이 들어오면 코드가 아래 순서로 실행된다. 이 순서대로 파일을 열어볼 것.

| # | 파일 | 볼 부분 | 포인트 |
|---|---|---|---|
| 1 | `src/main/java/com/lolstats/controller/SummonerController.java` | `getByRiotId()` | 요청의 입구. `triggerCollectionIfNeeded()` 헬퍼로 검색/갱신 두 엔드포인트의 중복 로직을 어떻게 묶었는지 |
| 2 | `src/main/java/com/lolstats/controller/PerIpRateLimitInterceptor.java` | 전체(짧음) | 컨트롤러 도달 **전**에 먼저 걸러짐. `WebConfig.java`에서 어떤 경로에만 붙는지(`addPathPatterns`)도 같이 볼 것 |
| 3 | `src/main/java/com/lolstats/service/SummonerService.java` | `findOrFetch()` → `recordSearch()` | 하단부에서 `ZINCRBY`(`opsForZSet().incrementScore`) 호출하는 부분 |
| 4 | `src/main/java/com/lolstats/client/RiotApiConfig.java` | `riotApiRateLimitBucket()`, `rateLimiting()` | Bucket4j 버킷이 `RestClient`에 인터셉터로 붙는 지점 — 실제 Riot 호출 직전 마지막 관문 |
| 5 | `src/main/java/com/lolstats/client/RiotApiClientImpl.java` | `withRetry()` | 429는 재시도, 401/403은 재시도 없이 즉시 실패 — 왜 다르게 처리하는지 주석 참고 |
| 6 | `src/main/java/com/lolstats/service/MatchService.java` | `planCollection()` vs `collectMatches()` | 왜 둘로 나뉘어 있는지: 전자는 검색 요청 "안에서" 동기로(가벼움, 1회 호출), 후자는 백그라운드에서(무거움, N회 호출) |
| 7 | `src/main/java/com/lolstats/service/MatchCollectionQueue.java` | 전체(143줄, 금방 읽음) | 이번 Phase의 핵심. 아래 §2 참고 |

### MatchCollectionQueue에서 특히 볼 부분

- **24~25번째 줄 근처** (`KEY_PREFIX`, `TTL` 상수 선언부) — Redis 키 하나(`collecting:{puuid}`)가 "지금 수집 중이냐"(키 존재 여부)와 "총 몇 개냐"(키의 값)를 동시에 표현하는 트릭. 별도 키 두 개를 안 쓴 이유
- **`runLoop()`** 메서드 vs **`process()`** 메서드 — `process()`가 `package-private`(접근제어자 없음)인 이유: 테스트에서 백그라운드 스레드 타이밍을 신경 안 쓰고 직접 호출하려고. `MatchCollectionQueueTest.java`와 짝지어 보면 이해가 빠름

---

## 2. 실제로 터졌던 버그 3개 (가장 남는 부분)

라이브 테스트 안 했으면 못 찾았을 것들. 이 세 개는 꼭 한번 볼 것.

### 2-1. 참가자 없이 저장되는 "고아 매치" — `MatchService.java`의 `saveMatch()`

매치 20건 중 일부를 지웠다 재수집시키는 라이브 테스트 중, `matches` 테이블엔 행이 생겼는데 `match_participants`가 0건인 데이터가 실제로 발견됨.

- **원인**: `matchRepository.save()`와 `matchParticipantRepository.saveAll()`이 각자 자기 트랜잭션(Spring Data JPA 리포지토리 메서드의 기본 동작)이라, 둘 사이에 예외가 나면 매치만 영구히 남음
- **왜 `@Transactional`을 안 쓰고 `TransactionTemplate`을 썼는지**: `saveMatch()`가 같은 클래스 안(`collectMatches()`)에서 호출되는데(self-invocation), 스프링의 `@Transactional`은 프록시 기반이라 이런 내부 호출에는 적용되지 않는 유명한 함정. `PlatformTransactionManager`를 직접 주입받아 `TransactionTemplate`으로 감싸서 우회

### 2-2. 워커 스레드가 조용히 죽을 수 있던 구조 — `MatchCollectionQueue.process()`의 다중 `catch`

`catch (HttpClientErrorException.Unauthorized | Forbidden)` 다음에 `catch (Exception e)`가 하나 더 있음(116번째 줄 근처). 이게 없으면:

- 예상 못 한 예외(위 2-1 같은 DB 문제 등) 하나가 `runLoop()`까지 뚫고 올라감
- `runLoop()`의 `catch`는 `InterruptedException`만 잡고 있어서, 다른 예외는 스레드를 그냥 죽여버림
- 재시작 전까진 아무도 매치를 안 가져오는 상태가 영구히 지속됨(요청 자체는 계속 200을 반환해서 겉으론 멀쩡해 보임 — 더 위험함)

### 2-3. Redis 타임아웃 미설정 — `application-dev.yml` / `application-prod.yml`의 `spring.data.redis.timeout: 500ms`

Task 3~5에서 "Redis 장애 시 fail-open"(`try { ... } catch (DataAccessException e) { ... }`) 코드를 열심히 짰는데, 이 설정 한 줄이 없어서 실제로는 무의미했음.

- Lettuce(Redis 클라이언트)의 기본 커맨드 타임아웃은 **60초**
- 요청 하나가 Redis를 여러 번 건드리면(per-IP 체크 + 인기검색어 랭킹 갱신 + 백그라운드 큐잉, 최대 3번) 최악의 경우 분 단위로 응답이 멈춤
- `500ms`로 짧게 잡아야 예외가 빨리 던져지고, fail-open 코드가 실제로 "빠르게 정상 동작"하게 됨
- 라이브로 확인: 설정 전엔 Redis를 내리면 응답이 2분 넘게 멈췄고, 설정 후엔 1.2초 만에 정상 응답

**교훈**: fail-open 로직은 "예외를 잡는 코드"만으로는 부족하고, "예외가 실제로 빨리 던져지는가"까지 확인해야 함 — 후자를 놓치기 쉬움.

---

## 3. 테스트 코드 (여유 있으면)

- `src/test/java/com/lolstats/service/MatchCollectionQueueTest.java` — `process()`를 직접 호출해서 백그라운드 스레드 타이밍 문제 없이 테스트하는 패턴. `process_pausesOnUnauthorized_...`, `process_doesNotDieOrPause_whenRedisUnavailable...` 같은 테스트명이 위 §2의 버그들과 1:1로 대응됨
- `src/test/java/com/lolstats/service/SummonerServiceTest.java` 하단부 — `_failsOpen_whenRedisUnavailable`류 테스트들, Redis가 죽어도 기능이 정상 동작하는지 검증하는 방식
- `src/test/java/com/lolstats/client/RiotApiConfigTest.java` — 실제 네트워크 호출 없이 Bucket4j 버킷 자체의 시간을 재서 "20개는 즉시, 21개부터는 대기"를 검증하는 방법(짧고 재밌음)

---

## 4. 참고 — 각 Task 커밋

| Task | 내용 | 커밋 |
|---|---|---|
| 0 | Redis 로컬 세팅 | `0bb9e03` |
| 1 | Bucket4j 전역 Rate Limiter | `c75b1ed` |
| 2 | 429 재시도 | `276213b` |
| 3 | 백그라운드 매치 수집 큐 | `19a5da8` |
| 4 | [전적 갱신] 버튼 | `5fb7468` |
| 5 | 인기 검색어 Redis 전환 | `b5dcdd8` |
| 6 | per-IP 요청 제한 | `dcbc5cf` |
| 7 | 캐시 무효화 전략 점검 | `50f89a6` |
| 8 | 테스트 정리 + DoD 마무리 | `0948e1e` |

각 커밋 메시지 자체에도 왜 그렇게 했는지 설명이 꽤 자세히 적혀 있어서, `git show <커밋>` 으로 커밋 메시지만 봐도 도움이 됨.
