# Phase 2 상세 작업 계획서 (성능/구조 개선)

> [PROJECT_PLAN.md](./PROJECT_PLAN.md) §4 Phase 2를 실행 단위로 쪼갠 작업 분해서(WBS). Phase 1([PHASE1_PLAN.md](./PHASE1_PLAN.md))과 동일한 형식 — 순서·산출물·완료 기준을 명시해 Claude Code 세션에서 항목 단위로 요청할 수 있게 함.
> 예산: **31~37h** (PROJECT_PLAN.md §10 기준, "전체에서 가장 무거운 Phase"). Phase 1 백엔드 실측은 추정 대비 약 5배 단축(20h→4h)됐지만 배율은 작업 유형마다 다름 — 이 Phase는 "수집 큐 실동작 검증" 비중이 커서(§10 실측 트래킹 메모) 단축 폭이 Phase 1보다 작을 것으로 예상. 실측은 진행하며 §4 DoD 아래에 기록.

## 0. 착수 전 확인 사항

- [ ] 로컬 Redis 실행 방법 확정 — `docker-compose.yml`에 `redis` 서비스 추가(예: `redis:7-alpine`, 포트 6379). **Phase 1에서 MySQL이 로컬 기존 서비스와 포트 충돌한 전례가 있으므로(3306→3307) Redis도 착수 전에 `6379` 포트가 비어 있는지 먼저 확인**
- [ ] `spring-boot-starter-data-redis` 추가 (Lettuce 클라이언트 포함, 별도 클라이언트 선택 불필요)
- [ ] Bucket4j 의존성 확정 — `bucket4j-core` 최신 안정판(단일 인스턴스라 `bucket4j-redis` 통합 불필요 — PROJECT_PLAN.md §4 Phase 2 명시). Spring Boot 4.1 신규 버전이라 착수 시점에 Maven Central에서 최신 버전·Java 17 호환 재확인 필요(PHASE1_PLAN.md §5의 Spring Boot 버전 변경 건과 동일한 이유로 미리 못 박지 않음)
- [ ] Redis 사용 방식 확정 — **Spring Cache 추상화(`@Cacheable` 등) 사용 안 함**. `StringRedisTemplate`으로 `SETNX`/`INCR`/`EXPIRE`/`ZINCRBY`/`ZREVRANGE` 원자 연산을 직접 호출(PROJECT_PLAN.md §2-6 학습 목적 — Redis 관용 패턴을 손으로 익히는 게 목적이라 추상화가 가리면 안 됨)

## 0.1 진행 현황 & 재개 방법

**아직 착수 전.** Phase 1 완료 시점(2026-07-29) 기준 다음 세션에서 Task 0부터 시작.

## 1. 이 문서 범위에 포함되지 않는 것

- **배포(EC2/도메인/HTTPS/CI-CD)** — PROJECT_PLAN.md §9. 캘린더상 3주차에 Phase 2 마무리와 겹치지만(§10 확정 캘린더), PROJECT_PLAN.md의 "Phase별 추정" 표에서도 배포는 Phase 2와 별도 줄(7~9h)로 잡혀 있음 — 이 문서 범위 밖, 별도로 진행
- Phase 3 통계 집계, 티어 이력 (§4 Phase 3)
- Phase 4 상대 챔피언 승률 (조건부, go/no-go 이후)
- Phase 5 JWT 로그인/즐겨찾기/마이페이지 — Redis는 이번 Phase에서 이미 도입하지만 "로그인 세션/Refresh Token 저장" 같은 인증 관련 용도는 Phase 5 몫

---

## 2. 작업 순서 (의존성 기준)

```
0. Redis 로컬 세팅
      │
1. Bucket4j 전역 Rate Limiter (Riot 호출 공통 관문)
      │
2. 429 재시도 로직 (Bucket4j로 감싼 클라이언트 위에 재시도 래핑)
      │
   ┌──┴───────────────┐
3. 백그라운드 매치 수집 큐   6. per-IP 요청 제한 (Redis만 있으면 되므로 독립적, 아무 때나 가능)
   (Redis collecting 플래그)
      │
4. [전적 갱신] 버튼
   (큐 트리거 + Redis 쿨다운)
      │
5. 인기 검색어 Redis ZSet 전환
      │
      └──────────┬───────────┘
                  ▼
      7. 캐시 무효화 전략 점검 (문서화 — 별도 구현 없음)
                  │
                  ▼
      8. 테스트 정리 / 누락분 보강
```

PROJECT_PLAN.md §4 Phase 2 원문의 항목 나열 순서(Bucket4j → per-IP → 큐 → 갱신버튼 → Redis → 캐시무효화 → 429재시도)는 주제별 묶음이라 실제 구현 순서와 다르다. **Redis는 큐(3)/갱신버튼(4)/인기검색어(5)/per-IP(6)가 전부 의존하므로 가장 먼저(Task 0)** 와야 하고, **429 재시도(2)는 큐(3)가 "탄력적인 클라이언트" 위에서 동작하도록 큐보다 먼저** 와야 한다 — Phase 1에서 Data Dragon을 의존성 기준으로 재배치했던 것과 같은 이유(§2).

---

## 3. 상세 작업 항목 (WBS)

### 0. Redis 로컬 세팅 — 0.5~1h

- [ ] `docker-compose.yml`에 `redis` 서비스 추가, `docker compose up -d`로 MySQL과 함께 기동 확인
- [ ] `application-dev.yml`에 `spring.data.redis.host/port` 추가
- [ ] `StringRedisTemplate` 빈이 정상 주입되는지 확인(기본 자동 설정 — 별도 `@Configuration` 불필요)

**완료 기준**: 앱 기동 로그에서 Redis 연결 확인 + 임시 테스트로 `SET`/`GET` 라이브 확인(확인 후 삭제).

### 1. Bucket4j 전역 Rate Limiter — 4~5h

- [ ] 버킷 정책: **초당 20회 + 2분당 100회**를 하나의 `Bucket`에 두 `Bandwidth`로 구성 (PROJECT_PLAN.md §4 실제 호출 횟수 계산 기준)
- [ ] 적용 지점: `RiotApiConfig`의 두 `RestClient`(`riotPlatformClient`/`riotRegionalClient`)가 **공유하는 하나의 `Bucket` 빈**을 `ClientHttpRequestInterceptor`로 통과시킴 — 전역 한도이므로 플랫폼/리전 클라이언트가 버킷을 나눠 가지면 안 됨. 메서드마다 수동으로 `tryConsume()` 호출하지 않아도 되는 구조(인터셉터 1곳)
- [ ] 토큰 소진 시 정책: 이 태스크에서는 **블로킹 대기**(`bucket.asBlocking().consume(1)`)로 최소 구현 — 실제 "거부 후 큐잉"은 Task 3(백그라운드 큐)의 몫이라 여기선 대기만으로 충분

**완료 기준**: 연속 25회 이상 Riot API 호출 시 21번째 호출부터 지연이 실제로 발생하는 걸 타이밍 로그로 라이브 확인(임시 테스트, 확인 후 삭제).

### 2. 429 재시도 로직 — 3~4h

- [ ] `RiotApiClientImpl`에서 `HttpClientErrorException.TooManyRequests` 캐치 시 `Retry-After` 헤더값만큼 대기 후 재시도(최대 재시도 횟수 상한 필요 — 무한 루프 방지)
- [ ] **Dev Key 만료(401/403) 시**: 재시도 의미 없으므로 즉시 실패 + 로그만 남김(§8 리스크 대응 — "큐 작업 연쇄 실패 방지"는 Task 3에서 큐 일시정지로 반영)

**완료 기준**: Mockito로 "1회 429 → 재시도 성공" 케이스, "401은 재시도 없이 즉시 예외 전파" 케이스 테스트.

### 3. 백그라운드 매치 수집 큐 — 6~8h

- [ ] 단일 워커 스레드가 인메모리 큐(`BlockingQueue<String>`, puuid)를 순차 소비하는 구조 (`@Component` + `@PostConstruct`로 워커 시작, `@PreDestroy`로 정지 — 매치별 `@Async` fan-out 금지, PROJECT_PLAN.md §4 명시 이유: Bucket4j 대기에서 스레드가 전부 잠드는 것 방지)
- [ ] 검색 시: DB에 있는 만큼 즉시 응답 + 부족분(나머지 매치 ID)은 큐에 적재, `Redis SETNX collecting:{puuid} true` + TTL 5분(동시 검색 시 중복 큐잉 방지 겸용)
- [ ] 워커가 매치를 하나 저장할 때마다 `collecting:{puuid}` **TTL 재연장**(하트피트) — 전역 한도 공유로 5분 초과가 정상 시나리오이므로 고정 TTL이면 진행 중에도 조기 만료됨(PROJECT_PLAN.md §4 상세 설명 참고)
- [ ] `GET /api/summoners/{summonerId}/matches` 응답에 `collecting`(bool)/`collectedCount`/`totalCount` 필드 추가 — FE 폴링용 (§7). `totalCount`는 매치 ID 목록 기준, `collectedCount`는 DB `COUNT` 파생
- [ ] Dev Key 만료(401/403) 감지 시 큐 처리 일시정지 + 로그(Task 2와 연결)

**완료 기준**: 신규 소환사 검색 후 매치 목록을 반복 조회하며 `collecting: true → false` 전이 확인, 워커를 재시작해도 이미 저장된 매치는 그대로 남아있음(원칙 ①) 확인, Dev Key를 일부러 무효화해 큐가 계속 실패 재시도하지 않고 멈추는지 확인.

### 4. [전적 갱신] 버튼 — 2~3h

- [ ] `POST /api/summoners/{summonerId}/refresh` — TTL 무관 강제 갱신(소환사 정보 즉시 갱신 + 신규 매치 큐잉)
- [ ] 쿨다운: `Redis SET NX EX cooldown:{summonerId}` — **큐잉 성공 후에 설정**(요청 진입 시점에 걸면 큐잉이 429/장애로 실패해도 쿨다운만 걸려 N분간 재시도 불가 — PROJECT_PLAN.md §4 명시)

**완료 기준**: 연타 시 쿨다운 기간 동안 명확한 거부 응답(429 또는 409 등 — 구현 시 확정), 큐잉 자체가 실패하면 쿨다운이 걸리지 않는 것 라이브 확인.

### 5. 인기 검색어 Redis ZSet 전환 — 2~3h

- [ ] 검색 성공 시 `SEARCH_COUNTS` DB 카운터 증가와 **함께** `ZINCRBY search_rank 1 {summonerId}` (정본은 SEARCH_COUNTS, Redis는 실시간 랭킹 캐시)
- [ ] `GET /api/summoners/popular` — **Redis `ZREVRANGE` 우선 조회**, Redis 장애/키 유실 시 SEARCH_COUNTS에서 재구성(fail-open, §8 리스크: Redis 장애 시 보호 기능만 잃고 본기능은 정상)

**완료 기준**: 정상 상태에서 Redis 값 기준 응답 확인 + **Redis를 강제로 내린 상태에서도** popular API가 DB 폴백으로 200을 반환하는 것 라이브 확인.

### 6. per-IP 요청 제한 — 2~3h

- [ ] 검색/갱신 트리거 엔드포인트에 필터 또는 인터셉터로 적용
- [ ] **Redis `INCR` + `INCR` 결과가 1일 때만 `EXPIRE`** 고정 윈도우 직접 구현 — `INCR`과 `EXPIRE` 사이 장애 시 TTL 없는 키가 영구 잔존해 해당 IP가 영구 차단되는 원자성 갭이 대표적 함정(PROJECT_PLAN.md §4 명시, README 트러블슈팅 기록 소재)
- [ ] 로컬 개발 환경은 `request.getRemoteAddr()` 그대로 사용 — 프록시 헤더(`X-Forwarded-For`) 처리는 배포 트랙(§9.2)에서, 이 문서 범위 밖

**완료 기준**: 짧은 시간에 한도를 초과하는 연속 요청 시 제한 응답 확인, 테스트로 "INCR 결과가 1일 때만 EXPIRE가 호출된다"를 직접 검증(원자성 갭 회귀 방지).

### 7. 캐시 무효화 전략 점검 — 1h

- [ ] 문서화 위주 태스크: 소환사 캐시 TTL(Phase 1에서 10분으로 확정 — PHASE1_PLAN.md §5)과 [전적 갱신] 버튼(Task 4)·백그라운드 큐(Task 3) 도입 후에도 이 TTL이 여전히 적절한지 재점검, 결론을 §5 결정 사항에 기록
- [ ] 별도 신규 코드 불필요 — 위 태스크들에 이미 반영된 내용을 정리하는 성격

**완료 기준**: 없음(설계 노트).

### 8. 테스트 정리 — 항목별 병행 + 마무리 1~2h

> PROJECT_PLAN.md §4 테스트 정책과 동일: 각 Task 구현과 같은 작업 단위에서 작성, 여기서는 빈틈만 확인.

- [ ] Bucket4j — 버킷 소진 시 대기/거부 동작
- [ ] 429 재시도 — 성공 재시도 케이스, 401 즉시 실패 케이스
- [ ] 백그라운드 큐 — 이미 있는 매치 skip(Phase 1 로직 재사용 확인), 워커 재시작 후 유실 없음
- [ ] per-IP 제한 — `INCR`+`EXPIRE` 원자성 갭 케이스
- [ ] Redis 장애 시 fail-open 동작(인기 검색어 DB 폴백)

---

## 4. Phase 2 완료 기준 (Definition of Done)

PROJECT_PLAN.md §4 Phase 2 체크리스트 전체 충족 + 아래 확인:

1. 신규 소환사 검색 시 즉시 일부 응답 + 나머지가 백그라운드로 채워지는 게 화면에서 눈으로 보임(폴링)
2. [전적 갱신] 연타 시 쿨다운이 걸리고, 갱신 자체는 실제로 새 매치를 큐잉함
3. 연속 과다 요청 시 Riot 전역 한도(Bucket4j)와 per-IP 제한이 각각 별도로 작동함을 확인
4. Redis를 강제로 내려도 검색/조회 본기능은 정상 동작(fail-open 확인)

### 실측 트래킹

| 항목 | 추정 | 실측 | 완료일 | 메모 |
|---|---|---|---|---|
| Redis 세팅 | 0.5~1h | | | |
| Bucket4j | 4~5h | | | |
| 429 재시도 | 3~4h | | | |
| 백그라운드 큐 | 6~8h | | | 실동작 검증 비중이 커서 단축 폭이 작을 것으로 예상 |
| 전적 갱신 버튼 | 2~3h | | | |
| 인기 검색어 Redis 전환 | 2~3h | | | |
| per-IP 제한 | 2~3h | | | |
| 테스트 정리 | 1~2h | | | |

---

## 5. 결정 사항 (확정)

| 항목 | 결정 | 비고 |
|---|---|---|
| Redis 클라이언트 | Spring Data Redis 기본값(Lettuce) | 별도 클라이언트 선택 불필요 |
| Redis 사용 방식 | `StringRedisTemplate` 원자 연산 직접 호출 | `@Cacheable` 등 캐시 추상화 미사용 — Redis 관용 패턴 학습이 목적(PROJECT_PLAN.md §2-6) |
| Bucket4j 분산 통합 | 미채용, in-memory 버킷만 | 단일 인스턴스 배포(§9.1)라 `bucket4j-redis` 통합은 의미 없음 |
| Bucket4j 적용 지점 | `RestClient` 공통 `ClientHttpRequestInterceptor` | 두 클라이언트(kr/asia)가 버킷 하나를 공유 — 전역 한도이므로 |
| 백그라운드 큐 동시성 모델 | 단일 워커 스레드 + 인메모리 `BlockingQueue` | 매치별 `@Async` fan-out 금지 — 전 태스크가 Bucket4j 대기에서 잠드는 것 방지(PROJECT_PLAN.md §4 명시) |
| per-IP 제한 구현 | Redis `INCR`+`EXPIRE` 고정 윈도우 직접 구현 | Bucket4j IP별 버킷 대신 — rate limiting 원리를 원자 연산으로 학습(§2-6) |
