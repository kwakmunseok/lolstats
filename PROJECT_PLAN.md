# 롤(LoL) 전적 검색 사이트 - 프로젝트 계획서 (v3.0)

> v2.x 리뷰 이력을 걷어낸 확정판. 변경 이력은 git이 담당한다.
> 프로젝트 루트에 `PROJECT_PLAN.md`로 저장해 Claude Code 세션 컨텍스트로 사용.

## 1. 프로젝트 개요

| 항목 | 내용 |
|---|---|
| 프로젝트명 | LoL Stats (확정) |
| 목적 | 백엔드 실무 역량 강화 (학습 프로젝트) |
| 대상 게임 | League of Legends |
| 데이터 소스 | Riot Games API |
| 개발 언어 | Java (Spring Boot) |
| 개발자 수준 | 기초~중급 CS 이해도 보유, Spring Boot 경험 있음, 이론(REST 원칙, Big-O 등) 보완 필요 |
| 작업 방식 | Claude Code와 협업하여 구현 |

### 왜 이 프로젝트인가
이미 OP.GG, Fow.kr 같은 상용 서비스가 있는 분야지만, 오히려 비교 대상이 명확해서 목표 수준을 잡고 "무엇을 개선했는지" 스스로 점검하기 좋다는 장점이 있음. 단순 클론이 아니라 아래 "차별화 포인트"를 통해 독자적인 가치를 만드는 것이 핵심.

---

## 2. 핵심 목표 (이 프로젝트로 기르고 싶은 역량)

1. **외부 API 연동 & Rate Limit 대응** — 캐싱 전략 설계 능력
2. **비동기/병렬 처리** — 다수의 매치 데이터를 효율적으로 수집
3. **데이터 모델링** — 관계형 DB 설계, 시계열성 데이터 처리
4. **테스트 코드 작성 습관** — JUnit + Mockito (각 Phase에서 기능과 함께 작성, Phase 5로 미루지 않음)
5. **배포 및 운영 경험** — Docker, CI/CD, 실제 URL로 접속 가능한 서비스 (§9 배포 계획 참고)
6. **Redis 자료구조·캐싱 패턴** — Sorted Set 실시간 랭킹, TTL 기반 쿨다운/락(`SET NX EX`), 원자 연산 rate limiting(`INCR`+`EXPIRE`). 라이브러리 뒤에 숨은 백엔드가 아니라 **Redis를 직접 만지는 역할**에 배치 (§3 캐시 행 참고)

---

## 3. 기술 스택

| 영역 | 기술 |
|---|---|
| 언어/프레임워크 | Java 17+, Spring Boot 3.x |
| 화면 (View) | Thymeleaf + Bootstrap 5 (CDN), vanilla JS/fetch |
| 메일 발송 | Spring Mail (`spring-boot-starter-mail`) + Gmail SMTP |
| DB | MySQL (§9 docker-compose 구성과 일치) |
| 캐시 | Redis (학습 목적 §2-6): ① 인기 검색어 실시간 랭킹(`ZINCRBY`/`ZREVRANGE` — 정본은 SEARCH_COUNTS, 유실 시 DB에서 재구성) ② [전적 갱신] 쿨다운(`SET NX EX`) ③ 수집 중복 큐잉 방지(`SETNX`+TTL) ④ per-IP 요청 제한(`INCR`+`EXPIRE`). 소환사 정보 캐싱은 DB(`updated_at`)가 담당. 자동완성 Redis 캐시는 미채용 — `game_name` 인덱스 LIKE로 충분 |
| 인증 | Spring Security + JWT (**httpOnly 쿠키 저장** — §6 회원 정책 참고) |
| API 문서화 | Swagger (springdoc-openapi) |
| 비동기 처리 | `@Async`, `CompletableFuture` |
| Rate Limiter | **Bucket4j (in-memory)** — "초당 20회 / 2분당 100회"를 애플리케이션 레벨에서 강제 (§4 아키텍처 원칙 ③). 앱 인스턴스 1대이므로 Redis 백엔드(bucket4j-redis) 통합 안 함 — 다중 인스턴스 확장 시에만 의미 |
| 게임 정적 자산 | **Riot Data Dragon(ddragon) CDN** — 챔피언/아이템/룬/스펠 아이콘 + ko_KR 매핑 JSON. API Key·Rate Limit 없음, 이미지는 CDN 핫링크 (§4 설계 노트) |
| 테스트 | JUnit5, Mockito |
| 배포 | Docker, Docker Compose, AWS EC2 프리티어 + Nginx + Let's Encrypt (§9 상세) |
| CI/CD | GitHub Actions → GHCR → EC2 (§9 상세) |
| 형상관리 | Git/GitHub |

> Claude Code로 작업할 때 이 표를 그대로 컨텍스트에 넣어주면 일관된 스택으로 코드를 생성해줄 확률이 높아짐.

> **화면 기술 선택 이유**: 백엔드 역량 강화가 목적이므로 프론트엔드 학습에 시간을 뺏기지 않도록 Thymeleaf(Spring Boot 서버사이드 템플릿)를 채택. React 대비 별도 빌드/배포/CORS 설정이 필요 없어 단일 애플리케이션으로 배포 가능. Bootstrap 5 기본 컴포넌트(카드, 네비바, 테이블, 탭)를 최대한 그대로 활용해 커스텀 CSS 작업을 최소화하고, 동적 요소(즐겨찾기 토글 등)는 vanilla JS + fetch API로 처리.

> **메일 발송 방식 선택 이유**: 자체 SMTP 서버는 구축하지 않음 (스팸 필터링/발신 도메인 신뢰도 문제로 실무에서도 거의 안 씀). 이 프로젝트 규모에서는 Gmail SMTP + 앱 비밀번호가 가장 빠르고 설정이 간단함(`spring-boot-starter-mail` 의존성 추가 후 몇 줄로 발송 가능). 실무에서는 AWS SES/SendGrid를 더 많이 쓰며, 필요시 README에 "실무에서는 SES/SendGrid로 대체 가능"이라고 언급하면 좋음. 로컬 개발 중에는 실제 메일 대신 콘솔 로그로 토큰을 출력하고, 배포 환경에서만 실제 발송하도록 프로필(dev/prod) 분리 권장.

---

## 4. 기능 명세 (MVP 우선순위)

### ⚠️ Riot API 제약 기반 아키텍처 원칙 (전 Phase 공통)

**실제 호출 횟수**: 신규 소환사 1명 조회 시 account-v1(1) + summoner-v4(1) + league-v4(1) + match-v5 ID 목록(1) + match-v5 상세 × 20 = **최대 24회**. Dev/Personal Key 한도(초당 20회, **2분당 100회**)에서 **연속 4~5명 검색이면 한도 소진** — 설계 결함이 아니라 이 API의 원래 특성이며, 아키텍처가 이 제약을 전제로 해야 한다. Personal Key도 rate limit은 Dev와 동일하므로 **이 제약은 영구적**이다(키 등급으로 해결 불가).

| # | 원칙 | 내용 |
|---|---|---|
| ① | **매치 영구 캐싱** | 매치는 이미 끝난 게임이라 절대 안 바뀜. `riot_match_id`가 DB에 있으면 **다시는 Riot API에 요청하지 않는다** (TTL 없음, 갱신 없음) |
| ② | **백그라운드 수집** | 검색 시 그 자리에서 20게임을 동기로 다 가져오지 않는다. DB에 있는 만큼 즉시 표시(없으면 기본 정보 + 상세 3건 정도만 우선 조회) → 나머지는 수집 큐에 넣고 백그라운드에서 Rate Limiter 속도 내로 채움 → 화면엔 "매치 데이터를 불러오는 중..." 로딩 표시 |
| ③ | **전역 Rate Limiter (Bucket4j)** | 모든 Riot API 호출은 Bucket4j 버킷(초당 20회/2분당 100회)을 통과해야 함. 이게 없으면 429가 떨어질 때까지 마구 호출하게 됨. **전역 버킷 = 1차 방어, 429 `Retry-After` 준수 재시도 = 최종 방어** — Riot은 전역 한도 외에 메서드별 한도도 있어 전역 버킷만으로는 429를 완전히 못 막는다 |
| ④ | **통계 스코프 = "DB에 누적된 매치 기준"** | "전체 기록 통계"는 불가능(챌린저 시즌 수백~수천 매치 = 수백~수천 회 호출 → 며칠 소요). 모든 통계(승률/폼/챔피언/상대전적)는 **우리 DB에 누적된 최근 N게임 기준**으로 정의. 검색이 쌓일수록 자연스럽게 풍부해지는 구조 — 약점이 아니라 "제약을 이해한 현실적 설계"로 README에 명시 |

> **리전 스코프 & 라우팅**: **KR 단일 리전 고정**. Riot API는 라우팅이 이원화되어 있다 — account-v1/match-v5는 지역 라우팅 **`asia`**, summoner-v4/league-v4는 플랫폼 라우팅 **`kr`**. API 클라이언트 설정에 두 base URL을 분리해 두어야 한다(하나로 합치면 구현 시 반드시 헷갈리는 지점). **league-v4는 `entries/by-puuid/{puuid}` 엔드포인트를 사용한다** — 전통적 예제의 `entries/by-summoner/{encryptedSummonerId}`는 SUMMONERS에 저장하지 않는 암호화 summoner id를 요구하므로 쓰지 않는다(by-puuid 사용으로 컬럼 추가 불필요).

> **Validation 정책 (전 폼 공통)**: FE는 HTML5 속성(`required`/`maxlength`/`pattern`) + JS 실시간 피드백으로 UX를 담당하고, BE는 `@Valid` + DTO 검증(`@NotBlank`/`@Size`/`@Email`/`@Pattern`)으로 최종 방어선을 담당한다. FE 검증은 우회 가능하므로 BE 검증이 항상 최종 기준. `@ControllerAdvice`로 검증 실패 응답 포맷을 통일한다.

> **테스트 정책 (전 Phase 공통)**: 각 Phase의 기능 구현과 **같은 작업 단위에서** Service 레이어 테스트를 함께 작성한다. Phase 5는 커버리지 보강만 담당. (마감 압박 시 가장 먼저 잘리는 위치에 테스트를 두지 않기 위함)

### Phase 1 — 기본기 (필수, MVP)
- [ ] Riot API 연동 (Development Key로 시작)
- [ ] 소환사 닉네임 검색 → 기본 정보 조회
- [ ] **DB 캐시 우선 조회** (SUMMONERS/MATCHES 테이블 → 만료 시에만 Riot API 호출) ← Rate Limit 때문에 Phase 1 필수. §8 리스크 참고
- [ ] 검색 자동완성 (DB에 캐싱된 소환사 대상, `game_name` prefix 검색)
- [ ] 검색 입력 제한 (게임 이름 3~16자, 태그라인 3~5자 — Riot 정책 기준, FE/BE 동시 검증)
- [ ] 검색 시 SEARCH_COUNTS 집계 (인기 검색어 데이터 소스 — §6 참고)
- [ ] 최근 매치 리스트 조회 — **Phase 1은 동기 최소 수집**: 매치 ID 20개 목록 + 상세는 3~5건만 즉시 조회, DB에 이미 있는 매치는 재요청 금지(원칙 ①). 20건 전체 백그라운드 수집은 Phase 2에서 전환. **429 시 실패 허용** — Phase 1엔 Bucket4j·재시도가 없으므로 429를 만나면 가져온 만큼만 부분 표시하고 중단(재시도·큐잉은 Phase 2)
- [ ] 매치 상세 정보 (KDA, 아이템, 룬 등) — 아이템/룬/스펠은 MATCH_PARTICIPANTS의 `items_json`/`runes_json`/`spell1_id·spell2_id`에 **Phase 1부터 저장**(§6 — 화면 요건이라 "확장 가능" 아님)
- [ ] **Data Dragon 연동** — versions.json 최신 패치 버전 캐싱(하루 1회 갱신) + champion/item/runesReforged `ko_KR` 매핑 JSON 앱 캐싱, 챔피언·아이템·룬·스펠·프로필 아이콘은 CDN 핫링크로 표시
- [ ] 티어 엠블럼(아이언~챌린저 문양) 정적 리소스 포함 — ddragon에 없으므로 Riot 개발자 포털 "Ranked Emblems" 자산을 프로젝트에 직접 포함
- [ ] 메인/프로필/매치 상세 화면 Thymeleaf + Bootstrap 기본 컴포넌트로 구현 (텍스트가 아닌 아이콘 기반 표시 — 챔피언 초상화, 아이템 6칸, 룬, 티어 엠블럼)
- [ ] Service 레이어 테스트 (Riot API 클라이언트는 Mockito로 mock)

> **자동완성 설계 노트**: Riot API는 prefix 검색을 지원하지 않아 정확한 gameName+tagLine만 조회 가능. 따라서 자동완성은 이미 캐싱된(과거 검색된) 소환사만 대상으로 하며, 서비스 초기엔 데이터가 적어 결과가 빈약할 수 있음 — README에 이 한계를 명시할 것. (game_name, tag_line)에 unique 제약이 없으므로(§6 닉네임 변경 정책) 동일 이름이 옛 주인/새 주인으로 중복 노출될 수 있다 — `ORDER BY last_searched_at DESC` + puuid 기준 dedupe로 처리.

> **Rate Limit 계산**: §4 상단 아키텍처 원칙 참고 — 신규 소환사 1명 = 최대 24회, 연속 4~5명 검색이면 2분 한도 소진. DB 캐시 + 동기 수집 최소화가 Phase 1에 있어야 하는 이유.

> **Data Dragon 설계 노트**: Match API는 `championId: 103` 같은 숫자만 반환하므로 ddragon 매핑 JSON으로 이름/이미지를 해석한다. 이미지 URL 패턴 — 챔피언 `https://ddragon.leagueoflegends.com/cdn/{버전}/img/champion/Ahri.png`, 아이템 `…/img/item/1001.png`, 스펠 `…/img/spell/SummonerFlash.png`, 프로필 아이콘 `…/img/profileicon/4568.png`, 룬은 runesReforged.json의 icon 경로를 `…/cdn/img/` 뒤에 결합. 매핑 데이터는 `…/cdn/{버전}/data/ko_KR/champion.json` 등에서 취득(한글명 포함). **버전 하드코딩 금지** — versions.json 최신값을 캐싱해 사용해야 패치 후 신규 챔피언/아이템 이미지가 깨지지 않는다. ddragon은 Riot API가 아니므로 Rate Limit 무관.

### Phase 2 — 성능/구조 개선
- [ ] **Bucket4j 전역 Rate Limiter** (초당 20회/2분당 100회 버킷, 모든 Riot 호출이 통과 — 원칙 ③)
- [ ] **per-IP 요청 제한** — 검색/갱신 유발 요청에 IP별 제한. Bucket4j IP별 버킷 대신 **Redis `INCR`+`EXPIRE` 고정 윈도우로 직접 구현**(rate limiting 원리를 원자 연산으로 학습 — §2-6). **`INCR` 결과가 1일 때만 `EXPIRE` 호출** — INCR와 EXPIRE 사이에 장애가 나면 TTL 없는 키가 영구 잔존해 해당 IP가 영구 차단되는 원자성 갭 방지(고정 윈도우 구현의 대표적 함정 — README 학습 기록 소재). 전역 버킷은 Riot 보호, per-IP는 사용자 간 공정성 담당(한 사용자의 연속 검색이 전역 한도를 혼자 소진하는 것 방지). 2계층 구조의 역할 분담을 README에 기록
- [ ] **매치 백그라운드 수집 큐** 도입 (검색 → DB분 즉시 표시 → 나머지 큐잉 — 원칙 ②) + 화면 로딩 표시. **구현 구조**: 매치별 `@Async` fan-out 금지 — 태스크 전부가 Bucket4j 대기에서 스레드를 물고 잠든다. **단일 워커 스레드가 인메모리 큐를 순차 소비**하는 구조로 구현(rate 소비가 자연히 직렬화되고 동시성 버그 여지가 줄어듦). FE는 매치 목록 응답의 수집 상태 필드(`collecting`, `collectedCount/totalCount`)를 폴링해 갱신 (§7). **수집 "진행 상태"의 저장 위치**: 수집된 **매치 데이터는 원칙 ①대로 DB에 영구 저장**되며 이것과 별개로, "지금 백그라운드 수집이 돌고 있는가"라는 일시적 진행 플래그는 **Redis `SETNX collecting:{puuid}` + TTL(예: 5분)**로 둔다 — 동시 검색 시 **중복 큐잉 방지와 겸용**(§2-6 학습 목적). **TTL 하트비트 연장**: 전역 한도(100회/2분)를 단일 워커 큐가 공유하므로 큐 대기까지 포함하면 5분 초과가 정상 시나리오 — 고정 TTL이면 수집 도중 플래그가 만료되어 같은 puuid 재큐잉·FE 폴링 조기 종료가 생긴다. **워커가 해당 puuid의 매치를 저장할 때마다 TTL을 재연장**하고, 워커가 죽었을 때만 TTL이 자연 만료되게 한다(재시작 유실 자연 해소라는 원래 의도 유지). `totalCount`는 매치 ID 목록 기준, `collectedCount`는 DB COUNT로 파생. 재시작·재배포 시 인메모리 큐는 유실되지만(수집된 매치는 전부 DB에 남음) 플래그는 **TTL 만료로 자연 해소** → `collecting: true`가 영구히 남아 FE가 무한 폴링하는 상태를 TTL이 방어. 부족분은 다음 검색/갱신 때 재큐잉
- [ ] **[전적 갱신] 버튼** — `POST /api/summoners/{summonerId}/refresh` (§7): TTL 무관 강제 갱신(소환사 정보 + 신규 매치 수집 큐잉), 연타 방지 쿨다운(갱신 후 N분 — **Redis `SET NX EX cooldown:{summonerId}`로 구현**, 쿨다운/락의 관용 패턴 학습 §2-6). **쿨다운은 큐잉 성공 후에 설정한다** — 요청 진입 시점에 놓으면 갱신이 429/장애로 실패해도 쿨다운만 타서 사용자가 N분간 재시도 불가. op.gg의 핵심 UX이자 Bucket4j·수집 큐가 실제로 동작하는지 눈으로 확인하기 가장 좋은 지점
- [ ] Redis 도입 (학습 목적 §2-6): 소환사 정보 캐싱은 Phase 1 DB 캐시(`updated_at`)가 담당(변동 없음). Redis는 ① **인기 검색어 실시간 랭킹**(`ZINCRBY search_rank 1 {summonerId}` + `ZREVRANGE` — **정본은 SEARCH_COUNTS**, 검색 시 DB 카운터와 ZSet을 함께 증가, Redis 유실 시 DB에서 재구성) ② [전적 갱신] 쿨다운(`SET NX EX`) ③ 수집 중복 큐잉 방지(`SETNX`+TTL) ④ per-IP 제한(`INCR`+`EXPIRE`) 담당. **전역 Bucket4j는 in-memory 유지**(단일 인스턴스 — bucket4j-redis 통합은 다중 인스턴스일 때만 의미). 자동완성 Redis 캐시는 미채용(DB 인덱스로 충분 — 투기적 설계). "DB로도 되는 것을 왜 Redis로 했나"에 대한 답(학습 목적 + 각 역할이 Redis 관용 패턴인 이유)을 README에 설계 근거로 기록
- [ ] 캐시 무효화 전략 설계 (소환사/티어 TTL, 갱신 주기)
- [ ] 429 재시도 로직 (`Retry-After` 준수) + **Dev Key 만료(401/403) 시 수집 큐 일시정지 + 로그**(만료된 키로 큐 작업이 연쇄 실패하는 것 방지, 키 교체 후 재개)
- [ ] Rate Limiter/큐/재시도 로직 테스트

### Phase 3 — 데이터/통계
> **통계 스코프**: 모든 통계는 "전체 기록"이 아니라 **DB에 누적된 매치 기준** (원칙 ④). 화면에 "최근 N게임 기준" 표기 필수.
> **큐 필터**: 승률/폼/챔피언 통계는 **랭크/드래프트 큐만 집계**(MATCHES.queue_type 필터 — Phase 4 상대전적과 동일 기준). 칼바람 등은 챔피언 랜덤성이 커서 섞으면 승률이 왜곡됨. 화면에 "랭크/드래프트 기준" 표기.
- [ ] 승률, 최근 폼(폼 추이), 챔피언별 통계 집계 — DB 누적 매치 기준. **MATCH_PARTICIPANTS 실시간 집계**(`WHERE puuid = ?` + `GROUP BY champion_id`) — 배치 테이블 없음(§6 설계 노트)
- [ ] 티어 변동 이력 추적 (시계열 데이터 — 검색/갱신 시점마다 스냅샷 적재. **직전 행과 tier/rank/LP 동일하면 INSERT 생략** — 인기 소환사 행 폭증 방지)
- [ ] 챔피언 통계/티어 이력 화면 (Bootstrap 탭 컴포넌트 활용, "N게임 기준" 표기)
- [ ] 집계 로직 테스트

### 시드 크롤러 — Phase 2 완료 후 추가할 "그 하나의 기능" (확정, grilling 2026-07-30)
> **명분 재정의**: 크롤러의 주목적은 **시연용 데이터 확보(콜드 스타트 해소)**이고 학습은 부차(단일 워커 큐·백필·스노볼). 남은 ~2주 리드의 **학습 주역은 소화 패스(§10)**, 크롤러는 그 옆에서 무인 가동하는 데이터 도구. 100회/2분 × 24h ≈ 일 최대 7.2만 호출 — 한도 우회가 아니라 **주어진 한도를 놀리지 않는 것**. **크롤은 최적화일 뿐 — 안 긁힌 티어도 첫 검색 시 on-demand로 조회·캐시된다(조회 불가 아님)**.
- [ ] **시드는 Riot 공식 API만 사용** (puuid 기반 — Riot ID+태그는 증분 생성 불가하지만 필요 없음). **3층 분리 구조**:
  - **① 시드 수집(저렴)**: `entries/{queue}/{tier}/{division}?page=N`을 **티어 순서(챌→그마→마스터→다이아 I→…)로 하강** 순회 → SUMMONERS 확보. 응답에 **puuid·티어·랭크·LP 포함**이라 소환사당 league-v4 재호출 불필요
  - **② 매치 ID 백필**: 각 puuid로 `match-v5 by-puuid/{puuid}/ids`
  - **③ 매치 상세 백필(비쌈 — 호출량의 대부분)**: 각 riot_match_id로 match-v5 상세, **DB에 있으면 스킵**(원칙 ①). 소환사당 20매치 캡
  - 이름 표시는 account-v1 by-puuid(§6), 매치 참가자 10인 puuid로 스노볼 확장 가능
- [ ] **(A) 로컬 전용 부트스트랩 도구 — EC2 미탑재(확정)** — 로컬에서만 수집, 원샷 업로드 후 **은퇴**. go-live 순간 실트래픽이 곧 예열 메커니즘이라 크롤러는 역할 종료. 배포 후 캐시는 실검색 on-demand + [전적 갱신] + 가벼운 TIER_HISTORY 갱신으로 성장 → **EC2 rate limit을 실사용자가 100% 전유**
- [ ] **착수 = 지금(TDD) + 데스크톱 24h 상시 가동** — 가치 = 벽시계 시간. 크롤러 먼저 빌드(1~2일)·가동 후 데이터가 백그라운드로 쌓이는 동안 사람 시간은 소화 패스(§10)에 투입. 데스크톱이라 무중단 가동 가능
- [ ] **하한 고정 없음 — 티어 순서 best-effort 하강** — Master+(챌+그마+마스터 ≈ 소환사 4~6천, 상위권 매치 중복 커 유니크 적음)는 **~1일에 보장 완료**, 그 뒤 남은 시간에 다이아 상위 디비전까지 되는 만큼. 다이아 완주는 목표 아님(인구 폭발+중복 감소로 ~10일+, 머신 가동시간이 실질 상한). **※ 인구/소요 수치는 개략 추정 — 착수 후 league-v4 `entries` 카운트로 실측**
- [ ] **업로드 = 타깃별 티어 슬라이스** — go-live 직전 `mysqldump` → `WHERE tier IN(...)`로 잘라 적재. **EC2엔 Master+**(1GB RAM 적정), 더 깊은 데이터는 **로컬 보존 → 향후 Oracle(24GB) 이전 시 활용**
- [ ] **원샷 클린 로드 — 빈 EC2 DB에 1회 적재(dedup 로직 불필요)**. 인프라 조기 배포(§9.6) 후 스택 검증 중 EC2 DB에 테스트 데이터가 쌓일 수 있으므로, **go-live 직전 EC2 DB `TRUNCATE` 후 적재**(또는 로드를 `--replace`로) — "빈 DB" 전제 보증
- [ ] ⚠️ **상용 전적 사이트(op.gg 등) 크롤링 절대 금지** — 동의한 General Policies가 "Riot API 외 소스 스크래핑" 명시 금지(API 접근 박탈). 국내 판례(여기어때/사람인)상 DB권·부정경쟁 리스크도 실재. 공식 API로 시드 확보 가능하므로 불필요
- [ ] 조건 ① **로컬 개발 중 수동 일시정지** — (A)로 EC2 상시 가동이 사라져 실사용자와의 rate limit 경쟁은 원천 소거. 남는 경쟁은 **로컬 개발 중 본인 테스트 호출 vs 크롤러**뿐 → 개발할 땐 크롤러 끄고, 자리 비울 때(야간 등) 돌리는 수동 스위치로 충분(정교한 우선순위 스케줄러 불필요)
- [ ] 조건 ② **SEARCH_COUNTS/Redis ZSet 증가 금지** — 크롤러 수집이 인기 검색어 오염 금지 (TIER_HISTORY 스냅샷은 적재 무방)
- 부수 이득: Phase 4 상대전적 통계의 표본을 크롤러가 직접 확보

### Phase 4 — 차별화 기능 (조건부: 3주차 말 go/no-go 판단)
> 3주차 종료 시점에 Phase 1~2가 계획대로 끝났을 때만 진행. 밀렸으면 **전체를 다음 버전으로 이월**하고 Phase 5 완성도에 집중. (미완성 차별화 기능 < 완성된 기본기 + 배포)
- [ ] 포지션별 상대 챔피언 승률 분석 (최소 버전) — **랭크/드래프트 큐만 집계**(칼바람 등은 `team_position`이 빈 값 — 큐 필터 전제)
- [ ] 상대 챔피언 승률 화면 (프로필 화면 내 탭으로 통합)
- 이월 후보: 듀오 시너지 통계, 룬/빌드 추천

### Phase 5 — 인증/마무리
> Phase 5 본선은 "JWT 로그인 + 즐겨찾기 + 마이페이지 + Swagger + CI/CD + README" — §10 "끝까지 지키는 것" 4개와 정합. 이메일 인증·비밀번호 재설정·로그인 잠금은 조건부(기본 이월).
- [ ] JWT 인증/인가 (httpOnly 쿠키 방식, 즐겨찾기·최근 검색 기록 등 개인화 기능과 연결)
- [ ] 로그인/회원가입/마이페이지 화면 (Bootstrap 폼 컴포넌트 활용)
- [ ] (조건부 — 기본 이월) 이메일 인증 + 재발송 + 비밀번호 재설정 플로우 + 메일 발송 설정(Gmail SMTP, dev/prod 프로필 분리) + 로그인 실패 잠금 — 1~4주차 실소요가 계획 내일 때만 진행. **이월 시 가입 즉시 활성**(§6 회원 정책 참고), EMAIL_VERIFICATION_TOKENS/PASSWORD_RESET_TOKENS 테이블도 함께 이월
- [ ] 전 폼 FE/BE Validation 통합 (`@Valid` + DTO 검증 + `@ControllerAdvice` 전역 예외 처리)
- [ ] 테스트 커버리지 보강 (각 Phase에서 작성한 테스트의 빈틈 메우기)
- [ ] Swagger API 문서화
- [ ] CI/CD 파이프라인 완성 (§9)
- [ ] README 작성 (ERD, 아키텍처 다이어그램, 트러블슈팅 기록, **서비스 URL**)

---

## 5. 화면 구성 및 사용자 플로우

### 화면 목록 (총 6개)

| # | 화면명 | 접근 권한 | 핵심 요소 |
|---|---|---|---|
| 1 | 메인 (검색) | 전체 공개 | 닉네임#태그 검색창, 최근 검색어(로컬), 인기 검색어(SEARCH_COUNTS 기반) |
| 2 | 소환사 프로필 | 전체 공개 | 티어/레벨/승률 요약, 최근 20게임 리스트, 챔피언별 통계 탭 |
| 3 | 매치 상세 | 전체 공개 | 양 팀 10명 KDA/아이템/룬 (타임라인은 명시 제외 — §10 이월 목록) |
| 4 | 로그인/회원가입 | 비로그인 전용 | 이메일/비밀번호, JWT 발급(httpOnly 쿠키) |
| 5 | 마이페이지 | 로그인 필요 | 즐겨찾기 소환사 목록, 최근 검색 기록 |
| 6 | 상대 챔피언 승률(Phase 4) | 전체 공개 | 프로필 화면 내 탭으로 통합 |

> **승률 표기 기준**: 승률이 두 곳에 두 기준으로 존재한다 — ① 프로필 **상단 요약**의 승률 = league-v4 시즌 wins/losses(솔로랭크, SUMMONERS 저장값) ② **통계 탭**의 승률/폼/챔피언 통계 = DB 누적 최근 N게임 기준(원칙 ④, 랭크/드래프트 큐 필터). 두 수치는 다를 수 있으므로 화면에 각각 "시즌 전체" / "최근 N게임 기준"을 표기한다.

### 비로그인 사용자 플로우

```
메인 화면 (닉네임#태그 검색)
      │
      ▼
소환사 조회 (DB 캐시 확인 후 필요시 Riot API 호출)
      │
   ┌──┴──┐
캐시 히트   캐시 미스 (Riot API 호출 후 저장)
   └──┬──┘
      ▼
프로필 화면 (티어·승률 요약, 최근 매치 목록)
      │
      ▼
매치 상세 화면 (팀별 KDA·아이템·룬)
```
비로그인 사용자도 전체 흐름 이용 가능 — 로그인은 즐겨찾기/기록 저장 시에만 필요.

### 로그인 사용자 플로우

```
로그인 (JWT 발급) → 마이페이지 (즐겨찾기·검색기록) → 프로필 화면 (바로가기 진입)
```
회원가입 없이도 기본 검색은 가능하며, 로그인은 개인화 기능 접근 시에만 요구됨.

---

## 6. 데이터 모델 상세 (ERD)

### 회원 정책

**회원가입 수집 정보**: 이메일(필수, 로그인 ID), 비밀번호(필수, bcrypt 해싱), 닉네임(필수), 약관 동의(필수), 마케팅 수신 동의(선택). 가입 직후 이메일 인증 전까지는 로그인 비활성 상태로 둔다 — 단 **이메일 인증은 조건부(기본 이월, §4 Phase 5)이며 이월 시 가입 즉시 활성**.

**로그인**: 이메일 + 비밀번호 검증 → Access Token(JWT, 15~30분) + Refresh Token(7~14일, **DB의 REFRESH_TOKENS 테이블에 해시 저장**하여 무효화 가능하게 관리) 발급. 로그인 5회 연속 실패 시 계정 일시 잠금(조건부 — 기본 이월. 구현 시 타인이 고의 실패로 남의 계정을 잠글 수 있는 표준 트레이드오프임을 README에 인지 기록).

**JWT 저장 위치 (확정)**: Access/Refresh Token 모두 **httpOnly + Secure 쿠키**에 저장한다. 이유: ① Thymeleaf 서버 렌더링 페이지 이동에는 Authorization 헤더를 붙일 수 없음 ② localStorage 저장은 XSS에 취약. Spring Security 필터에서 쿠키의 토큰을 파싱해 인증 처리. CSRF는 SameSite=Lax + 상태 변경 API의 CSRF 토큰으로 방어. (README 트러블슈팅에 선택 근거 기록 권장)

**아이디 찾기는 제공하지 않음**: 이메일 자체가 로그인 ID이며, 닉네임으로 이메일을 조회하게 하면 이메일 주소 노출(enumeration) 공격에 취약해짐. 대신 비밀번호 재설정만 제공.

**비밀번호 재설정 플로우**:
1. 이메일 입력 → 서버는 존재 여부와 무관하게 항상 동일한 응답("메일을 발송했습니다") 반환 — 계정 존재 여부 노출 방지
2. 실제 존재하면 1회용 재설정 토큰(유효 30분) 이메일 발송
3. 링크 클릭 → 새 비밀번호 입력 → 토큰 검증 후 변경, 토큰 즉시 만료

### 엔티티 관계
- `USERS` 1:N `FAVORITES`, 1:N `SEARCH_HISTORY`
- `SUMMONERS` 1:N `FAVORITES`, `SEARCH_HISTORY`, `TIER_HISTORY`, 1:1 `SEARCH_COUNTS`
- `MATCHES` 1:N `MATCH_PARTICIPANTS`
- `MATCH_PARTICIPANTS`는 SUMMONERS와 **FK로 연결하지 않고 puuid 스냅샷으로 느슨하게 연결** (아래 설계 노트)

> **MATCH_PARTICIPANTS 설계 노트**: 매치 1건에는 참가자 10명이 있고, 그중 대부분은 검색된 적 없는 소환사라 SUMMONERS 테이블에 없다. SUMMONERS FK를 강제하면 매치 저장 시마다 티어/레벨이 비어 있는 껍데기 소환사 행을 10명분 만들어야 한다. 따라서 참가자에는 Riot 매치 응답에 포함된 `puuid` + 닉네임 스냅샷을 직접 저장한다(상용 전적 사이트의 표준 방식 — 매치 시점 닉네임이 보존되는 부수 효과도 있음). 특정 소환사의 매치 조회는 `WHERE puuid = ?` 인덱스 검색으로 처리.

> **닉네임 변경 정책**: Riot ID는 변경 가능하고, 버려진 이름은 타인이 가져갈 수 있다. **정체성은 puuid, 이름은 스냅샷** — 따라서 (game_name, tag_line)에 unique 제약을 걸지 않는다. TTL 갱신·[전적 갱신] 시 account-v1 **by-puuid**로 이름을 재동기화한다. 이름으로 DB 히트했더라도 TTL 만료 후 account-v1 by-riot-id 결과의 puuid가 기존 행과 다를 수 있음(이름 주인이 바뀐 경우) → 이때는 새 puuid 기준으로 행을 조회/생성하고, 옛 주인의 행은 다음 갱신 때 이름이 재동기화된다. 자동완성에 옛 이름이 잠시 남을 수 있는 한계는 README에 기록.

**테이블 상세**

```
USERS
- id (PK, bigint)
- email (string, unique)
- password_hash (string)
- nickname (string)
- email_verified (boolean, default false)
- login_fail_count (int, default 0)
- locked_until (datetime, nullable) — 로그인 잠금 해제 시각
- created_at (datetime)

EMAIL_VERIFICATION_TOKENS
- id (PK, bigint)
- user_id (FK → USERS)
- token (string, unique)
- expires_at (datetime)
- used (boolean, default false)

PASSWORD_RESET_TOKENS
- id (PK, bigint)
- user_id (FK → USERS)
- token (string, unique)
- expires_at (datetime) — 발급 후 30분
- used (boolean, default false)

REFRESH_TOKENS
- id (PK, bigint)
- user_id (FK → USERS)
- token_hash (string) — 원문 저장 금지, 해시로 저장
- expires_at (datetime)
- revoked (boolean, default false) — 로그아웃/재발급 시 무효화

SUMMONERS
- id (PK, bigint)
- puuid (string, unique) — Riot 고유 식별자 (league-v4도 by-puuid로 조회 — §4 라우팅 노트)
- game_name (string) — 자동완성용 인덱스 필요 (LIKE 'query%' 검색)
- tag_line (string)
- profile_icon_id (int)
- summoner_level (int)
- tier (string) / rank (string) / league_points (int) — **RANKED_SOLO_5x5(솔로랭크) 기준, 언랭 시 null** (league-v4는 큐별 배열 반환 — 큐 선택을 명시하지 않으면 구현 시 흔들림)
- wins / losses (int) — league-v4 응답 저장 (§7 응답 예시와 정합, 솔로랭크 기준)
- updated_at (datetime) — 캐시 갱신 기준 시각

MATCHES
- id (PK, bigint) — 내부 식별자
- riot_match_id (string, unique) — Riot API 매치 ID
- game_creation (datetime)
- game_duration (int, 초 단위)
- queue_type (string) — 통계 큐 필터 기준 (§4 Phase 3)

MATCH_PARTICIPANTS  ※ SUMMONERS FK 없음
- id (PK, bigint)
- match_id (FK → MATCHES)
- puuid (string, index) — 소환사 조회 키 (SUMMONERS와 FK 없이 느슨한 연결)
- game_name / tag_line (string) — 매치 시점 닉네임 스냅샷
- champion_id (int)
- team_position (string)
- kills / deaths / assists (int)
- win (boolean)
- spell1_id / spell2_id (int) — 스펠 아이콘 표시용
- items_json (json) — 아이템 6칸 + 장신구
- runes_json (json) — 룬 (주/부 트리 + 핵심 룬)
※ 아이템/룬/스펠은 Phase 1 화면 요건(아이콘 기반 표시)이 이미 요구하므로 **Phase 1 필수**. 서브테이블 대신 JSON 컬럼 채택(집계 대상이 아니고 표시 전용이라 정규화 이득 없음)

SEARCH_COUNTS  ※ 인기 검색어 데이터 소스 (정본)
- summoner_id (PK, FK → SUMMONERS) — 1:1
- search_count (bigint, default 0) — 비로그인 포함 전체 검색 시 증가
- last_searched_at (datetime)
※ 인기 검색어 = search_count 상위 N (필요시 last_searched_at로 최근성 보정)
※ 행이 검색당 늘어나는 로그 방식 대신 소환사당 1행 카운터 방식 — 단순하고 조회 빠름

CHAMPION_STATS — 미채용(이월)
※ MVP는 MATCH_PARTICIPANTS 실시간 집계로 대체: WHERE puuid = ? + GROUP BY champion_id
※ 현 스코프(소환사당 수십~수백 행, puuid 인덱스)에선 밀리초 수준 — 배치 테이블은 투기적 설계
※ 데이터 증가로 실측 지연이 생기면 그때 배치 집계 테이블 도입 — 전환 과정 자체를 README 트러블슈팅 소재로

TIER_HISTORY (시계열)
- id (PK, bigint)
- summoner_id (FK → SUMMONERS)
- tier / rank (string)
- league_points (int)
- recorded_at (datetime)
※ 직전 행과 tier/rank/league_points 동일하면 INSERT 생략 (검색당 중복 스냅샷 방지)
※ 언랭(tier null — 시즌 리셋 포함) 시에도 INSERT 생략 — 시계열은 랭크 보유 시점만 기록

FAVORITES
- id (PK, bigint)
- user_id (FK → USERS)
- summoner_id (FK → SUMMONERS)
- created_at (datetime)

SEARCH_HISTORY — 로그인 사용자 개인 기록 전용 (인기 검색어와 무관)
- id (PK, bigint)
- user_id (FK → USERS)
- summoner_id (FK → SUMMONERS)
- searched_at (datetime)
```

---

## 7. API 엔드포인트 명세

### 인증 (Auth)
> `verify-email` / `resend-verification` / `password/forgot` / `password/reset` 4개는 조건부(기본 이월 — §4 Phase 5). 이월 시 미구현.

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/auth/signup` | 불필요 | 회원가입 (이메일/비밀번호/닉네임/약관동의) |
| POST | `/api/auth/verify-email` | 불필요 | 이메일 인증 토큰 검증 |
| POST | `/api/auth/resend-verification` | 불필요 | 인증 메일 재발송 |
| POST | `/api/auth/login` | 불필요 | 로그인, JWT access/refresh 쿠키 발급 (5회 실패 시 잠금) |
| POST | `/api/auth/logout` | JWT 필요 | Refresh Token 무효화 + 쿠키 삭제 |
| POST | `/api/auth/refresh` | Refresh Token | access token 재발급 |
| POST | `/api/auth/password/forgot` | 불필요 | 비밀번호 재설정 메일 발송 (존재 여부 무관 동일 응답) |
| POST | `/api/auth/password/reset` | 불필요(토큰 검증) | 토큰 검증 후 새 비밀번호 설정 |

### 소환사 (Summoner)
> **경로 설계 노트**: Riot ID 조회는 `/riot-id/` prefix로 분리한다. `/{gameName}/{tagLine}` 같은 2세그먼트 전면 와일드카드를 `/{summonerId}/matches` 등과 같은 prefix 아래 두면 라우팅 모호성 footgun이 된다(리터럴 우선 매칭에 기대는 구조 — 엔드포인트 추가 때마다 충돌 검토 필요).

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/summoners/riot-id/{gameName}/{tagLine}` | 불필요 | 소환사 기본 정보 조회 (캐시 우선, 만료 시 Riot API 갱신). 조회 시 SEARCH_COUNTS 증가(Phase 2부터 Redis ZSet `ZINCRBY` 동시 증가) — **GET의 부작용은 인지된 선택**: 크롤러·새로고침 중복 집계 한계와 함께 README 트러블슈팅에 근거 기록 |
| POST | `/api/summoners/{summonerId}/refresh` | 불필요 | **[전적 갱신]** — TTL 무관 강제 갱신(소환사 정보 즉시 갱신 + 신규 매치 수집 큐잉). 갱신 후 N분 쿨다운으로 연타 방지 (쿨다운은 Redis `SET NX EX` — 별도 테이블/컬럼 없음. **큐잉 성공 후 설정** — §4 Phase 2) |
| GET | `/api/summoners/autocomplete?query=&limit=` | 불필요 | 검색 자동완성 (DB 캐싱된 소환사 대상 prefix 검색, puuid 기준 dedupe — §4 설계 노트) |
| GET | `/api/summoners/popular?limit=` | 불필요 | 인기 검색어 — **Redis `ZREVRANGE` 우선, miss(유실) 시 SEARCH_COUNTS에서 재구성**(정본은 DB). Phase 1(Redis 도입 전)은 DB 직접 조회 |
| GET | `/api/summoners/{summonerId}/matches?page=&size=` | 불필요 | 최근 매치 목록 페이지네이션 조회. 응답에 수집 상태 포함(`collecting: bool`, `collectedCount/totalCount`) — 백그라운드 수집 중 FE 폴링용 |
| GET | `/api/summoners/{summonerId}/champion-stats` | 불필요 | 챔피언별 승률/평균 KDA 집계 (랭크/드래프트 큐만 — §4 Phase 3 큐 필터) |
| GET | `/api/summoners/{summonerId}/tier-history` | 불필요 | 티어 변동 이력 (시계열) |
| GET | `/api/summoners/{summonerId}/matchup-stats?position=&opponentChampionId=` | 불필요 | (Phase 4) 포지션별 상대 챔피언 승률 (§4 기능 명세와 일치하도록 position 파라미터 포함) |

### 매치 (Match)
| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/matches/{riotMatchId}` | 불필요 | 매치 상세 (양팀 10명, 아이템/룬 포함). URL 식별자는 내부 PK가 아닌 `riot_match_id` — 공유 가능한 URL |

### 마이페이지 (즐겨찾기/기록)
| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/users/me/favorites` | JWT 필요 | 즐겨찾기 소환사 목록 |
| POST | `/api/users/me/favorites` | JWT 필요 | 즐겨찾기 추가 (`{ summonerId }`) |
| DELETE | `/api/users/me/favorites/{summonerId}` | JWT 필요 | 즐겨찾기 삭제 |
| GET | `/api/users/me/search-history` | JWT 필요 | 최근 검색 기록 조회 |

### 응답 예시 — 소환사 조회

```json
GET /api/summoners/riot-id/Hide%20on%20bush/KR1

{
  "id": 1024,
  "gameName": "Hide on bush",
  "tagLine": "KR1",
  "profileIconId": 4568,
  "summonerLevel": 612,
  "tier": "CHALLENGER",
  "rank": "I",
  "leaguePoints": 1487,
  "wins": 312,
  "losses": 198,
  "updatedAt": "2026-07-28T09:12:00Z"
}
```

> tier/rank/leaguePoints/wins/losses는 **RANKED_SOLO_5x5 기준**, 언랭이면 null (§6 SUMMONERS 참고).

---

## 8. 예상 리스크 & 대응

| 리스크 | 대응 방안 |
|---|---|
| Development Key 24시간 만료 | **Personal API Key 즉시 신청(1주차)** — 무료, 24h 만료 해결. 공식 요건상 검증 절차 없이 상세 설명만 요구(동작 화면·스크린샷 불필요 — Production만 프로토타입 요구). 리뷰 소요 편차 대비 선신청, 승인 전까진 Dev Key 매일 갱신으로 감수. 단 rate limit은 Dev와 동일하므로 아키텍처 제약은 그대로 (Production Key는 실사용자 있는 서비스용이라 신청 사유가 약함 — 신청 안 함) |
| Rate Limit 초과 (신규 소환사 1명 = 최대 24회, 연속 4~5명이면 2분 한도 소진) | §4 아키텍처 원칙: 매치 영구 캐싱 + 동기 수집 최소화(Phase 1) → Bucket4j 전역 리미터 + 백그라운드 수집 큐 + 429 재시도(Phase 2) |
| 비인증 남용: 검색/갱신이 비인증 + Riot 버킷은 전역 하나 → 한 사용자가 임의 소환사 연속 조회로 전체 수집을 고갈 가능(소환사별 쿨다운으로는 방어 불가) | **per-IP 요청 제한을 Phase 2에서 구현**(Redis `INCR`+`EXPIRE` 직접 구현, +1h 수준). 2계층 구조(Riot 보호 / 사용자 간 공정성)의 역할 분담을 README에 기록 |
| 통계 데이터 부족 (서비스 초기 DB 누적 매치가 적음) | 통계 스코프를 "DB 누적 기준"으로 정의하고 화면에 "N게임 기준" 표기. README에 설계 근거 명시. Phase 2 완료 후 **시드 크롤러**(§4 선택 작업)로 유휴 시간 사전 캐싱 가능 |
| Riot API 응답 스키마 변경 | DTO 계층 분리, API 버전 관리 |
| LoL 패치 후 아이콘 깨짐 (신규 챔피언/아이템) | ddragon 버전 하드코딩 금지 — versions.json 최신값 캐싱(하루 1회 갱신) 사용 (§4 설계 노트) |
| 데이터량 증가로 DB 성능 저하 | 인덱스 설계(MATCH_PARTICIPANTS.puuid 등), 배치 집계 테이블 분리 |
| EC2 프리티어 메모리 부족 (1GB에 app+MySQL+Redis) | swap 2GB 설정 + JVM 힙 제한(`-Xmx384m` 수준) + Redis `maxmemory 64mb` + `maxmemory-policy allkeys-lru` 설정(용도가 전부 소용량 키라 충분). 그래도 부족하면 **Oracle Cloud Always Free(24GB RAM) 이전** — §9.1 이전 후보 참고(마감 후 권장) |
| Redis 장애 (쿨다운·중복 큐잉 방지·per-IP 제한이 전부 Redis) | **fail-open**: Redis 접속 불가 시 보호 기능만 잃고 검색/조회 본기능은 정상 동작(예외는 로그만 남기고 통과, 인기 검색어는 DB 폴백). 학습 프로젝트 규모에서 Redis 때문에 서비스 전체가 죽는 fail-closed는 과잉 |
| 5주차 과부하 | Phase 2를 1주로 압축, Phase 5를 4~5주차에 분산, Phase 4는 조건부 (§10 재배분 완료) |

---

## 9. 배포 계획

> 목표: 마감 시점에 **실제 URL(HTTPS)로 접속되는 서비스** + push하면 자동 배포되는 CI/CD. 실제 URL로 운영까지 해봐야 핵심 목표 5(배포·운영 경험)가 완성된다.

### 9.1 인프라 선택

| 선택지 | 비용 | 장점 | 단점 |
|---|---|---|---|
| **AWS EC2 프리티어 (t3.micro/t2.micro)** ← 채택 | 12개월 무료 | 실무에서 가장 널리 쓰이는 표준 환경 | RAM 1GB → swap 필수, 12개월 후 유료 |
| Oracle Cloud Always Free (ARM) | 평생 무료 | RAM 최대 24GB로 여유 | 계정 정지 사례 있음, 국내 실무 사용 사례 적음 |
| PaaS (Railway, Fly.io 등) | 무료~소액 | 설정 최소 | 인프라를 직접 다루지 않아 "배포 및 운영 경험"을 얻기 어려움 (핵심 목표 5번과 충돌) |

**채택: AWS EC2 프리티어 1대에 Docker Compose로 전부 올림** (app + MySQL + Redis + Nginx). RDS/ElastiCache 분리는 프리티어 기간이 별도로 돌고 관리 포인트만 늘어 이 규모에선 불필요 — README에 "실무 규모에서는 RDS/ElastiCache 분리" 한 줄이면 충분.

> **왜 EC2 우선인가 (마감 기준 결정)**: Oracle Always Free가 "영구 무료 + 24GB RAM(메모리 압박 해소)"으로 더 매력적이지만, 마감(08/31)이 걸린 이 프로젝트에선 **A1 ARM 인스턴스 용량 부족**이 결정적 리스크다 — 서울 포함 인기 리전에서 Always Free A1 생성이 "out of capacity"로 상습 실패(수 시간~수일 재시도)하며, 배포일에 인스턴스가 안 잡히면 일정이 통째로 흔들린다. EC2 프리티어는 이 리스크가 0이고 12개월 무료면 마감·평가·시연을 충분히 커버한다. 두 옵션 모두 "직접 서버 운영"이라 핵심 목표 5(배포·운영 경험)의 학습 가치는 동등하므로, 안정성으로 EC2를 택한다.

> **Oracle Cloud Always Free — 마감 후 상시 운영 이전 후보**: 마감 이후 서비스를 오래 띄워둘 경우 Oracle로 이전(비용 영구 무료 + 메모리 여유로 §8 swap/`-Xmx384m`/Redis `maxmemory` 쥐어짜기 전부 불필요). 준비: ① **지금 미리 A1 인스턴스 생성 시도(확정 — 병렬 진행)**해두면(용량 잡힐 때까지) 나중 이전이 수월. 확보만 해두고 세팅은 마감 후. **홈 리전 = Tokyo(ap-tokyo-1) 확정** — 한국(서울/춘천)은 신규 가입 홈 리전으로 미제공, 홈 리전은 가입 시 1회 선택·영구 불변이며 Always Free 자원은 홈 리전에만 생성되므로 한국 최근접인 도쿄 선택(한국↔도쿄 ~30ms). Riot API는 서버 위치 무관(항상 asia/kr 라우팅)이라 수집 성능 영향 없고, 마감 본진은 서울 EC2라 한국 리전 요건은 그쪽이 충족. **가입 상태: 집에서 진행 예정** — Oracle 가입은 결제수단(해외결제 가능 카드) 등록 필수(Always Free만 써도 필요, $1 임시 승인 후 취소·실청구 없음). 카드가 집에 있어 보류. 가입 시 첫 30일 $300 Free Trial로 시작되나 **"Always Free eligible" 표시 Shape만** 선택하면 트라이얼 종료 후에도 과금 없음(A1 4 OCPU/24GB·부트볼륨 무료 한도 내). 마감 본진은 EC2라 Oracle 지연돼도 계획 무영향 ② 아키텍처가 **ARM(arm64)** 이므로 Docker 이미지를 arm64로 빌드해야 함(GitHub Actions에 `platforms: linux/arm64` 추가 — CI 한 줄) ③ 유휴 자원 회수·계정 정지 사례가 있으므로 상시 운영 시 백업(mysqldump) 원격 보관 권장. EC2에서 완성한 docker-compose 구성을 그대로 옮기면 되므로 이전 비용은 낮음.

### 9.2 구성도

```
[사용자] ─HTTPS─> [Nginx (80/443, 리버스 프록시 + Let's Encrypt 인증서)]
                        │
                        ▼
                  [Spring Boot app 컨테이너 (8080)]
                        │                │
                        ▼                ▼
                  [MySQL 컨테이너]   [Redis 컨테이너]
              (전부 동일 EC2, docker-compose.yml 1개로 관리)
```

> **프록시 헤더 설정 (필수)**: Nginx에 `proxy_set_header X-Forwarded-For` / `X-Forwarded-Proto` + Spring에 `ForwardedHeaderFilter`(또는 `server.forward-headers-strategy=framework`) 설정. 없으면 ① per-IP 제한이 모든 사용자를 Nginx IP 하나로 인식해 전 사용자 공용 버킷이 되어 무용지물 ② 앱이 요청을 HTTP로 오인해 Secure 쿠키·리다이렉트가 오동작. 배포 아키텍처(§9.2)와 per-IP 제한(§8)이 만나는 지점의 필수 배선 — README 트러블슈팅 소재.

### 9.3 도메인 & HTTPS

- **도메인 (자체 구매 확정)**: 가비아/Cloudflare에서 `.com`/`.kr` 구매 (연 ₩13,000~20,000 — 이 프로젝트의 유일한 고정 비용). 도메인 등록·DNS 설정 경험 확보 목적. (DuckDNS 무료 서브도메인은 미채용)
- **HTTPS**: Nginx + Let's Encrypt(certbot) 무료 인증서, 자동 갱신 cron 설정. JWT를 Secure 쿠키로 쓰므로 HTTPS는 선택이 아닌 필수.

### 9.4 CI/CD (GitHub Actions)

```
git push (main)
  → GitHub Actions: 테스트 실행 (실패 시 배포 중단)
  → Docker 이미지 빌드 → GHCR(GitHub Container Registry) push
  → EC2에 SSH 접속 → docker compose pull && docker compose up -d
```
- 필요한 GitHub Secrets: EC2 SSH 키, GHCR 토큰
- 핵심: 무중단까지는 안 가더라도 "테스트 통과 없이는 배포 안 됨" 게이트를 두는 것 — 과정을 트러블슈팅에 기록
- **CI 테스트 인프라**: 게이트 대상은 Mockito 단위 테스트 중심(DB 불필요 — §4 테스트 정책과 일치). DB 의존 통합 테스트가 생기면 **Testcontainers** 사용(GitHub Actions 러너에서 동작, 로컬은 Docker 필요). H2는 MySQL 방언 차이(JSON 컬럼·LIKE collation 등)로 미채용

### 9.5 시크릿 관리

- Riot API Key, DB 비밀번호, JWT 시크릿, Gmail 앱 비밀번호 → **절대 커밋 금지**. 서버의 `.env` 파일 + docker-compose `env_file`로 주입, CI에서는 GitHub Secrets 사용
- `application-dev.yml` / `application-prod.yml` 프로필 분리 (메일 발송 정책과 동일 기준)

### 9.6 배포 시점 전략 — "3주차에 먼저 올린다"

배포를 Phase 5(마지막 주)에 처음 시도하면 서버 세팅 문제가 마감 직전에 터진다. **3주차에 Phase 1~2 결과물을 수동으로 먼저 배포**(walking skeleton)하고, 이후 기능이 추가될 때마다 재배포하면서 CI/CD는 Phase 5에서 완성한다.

| 시점 | 작업 | 예상 시간 |
|---|---|---|
| 1주차 중 | 도메인 구매, EC2 인스턴스 생성만 해둠 + **Personal API Key 신청**(검증 절차 없음 — 리뷰 대기를 미리 소화, 승인 시 개발 중 매일 키 갱신 마찰도 제거) | 1~2h |
| **인프라 조기 배포(★ grilling 07-30, 리드 활용)** | EC2 세팅(Docker, swap) + Nginx/HTTPS + **빈 스택 먼저 올림**(URL 비공개). 프리티어라 조기 가동해도 $0, §9.6 "배포 리스크 조기 소진"을 2주 리드로 앞당김 | 4~5h |
| go-live 직전 | **크롤 데이터 원샷 로드**(빈 EC2 DB `TRUNCATE` 후 Master+ 슬라이스 적재, §4·§9.8) + 도메인 연결 → 실서비스 개시 | 1h |
| 5주차 | GitHub Actions CI/CD 완성 | 2~3h |

### 9.7 운영 최소한

- 로그: `docker compose logs` + Spring Boot 파일 로그면 충분 (ELK 등은 오버엔지니어링)
- 모니터링: UptimeRobot 무료 플랜으로 다운 감지 알림 정도만
- 백업: MySQL 볼륨 주 1회 `mysqldump` cron — 학습 프로젝트 데이터라 이 이상은 불필요

### 9.8 데이터 영속성 & 서버 이관

- **MySQL named volume 필수** (`mysql-data:/var/lib/mysql`) — 컨테이너 재생성·`compose down/up`에도 DB 보존. 크롤링(§4)으로 채운 데이터가 여기 의존
- **Redis 볼륨은 선택** — 캐시·휘발성 키(쿨다운/큐 플래그/per-IP/인기검색어)뿐이라 없어도 무방, 새 서버에서 빈 상태로 시작해도 DB에서 재구성(§8 fail-open)
- **정적 파일은 볼륨 불필요** — ddragon 아이콘=CDN 핫링크(저장 안 함), 티어 엠블럼=이미지에 패키징되어 이미지와 함께 이동
- ⚠️ **볼륨은 호스트 디스크에 귀속 — 서버 이관 시 따라오지 않는다.** 이관 수단은 `mysqldump`(논리 백업, **x86↔ARM 아키텍처 무관 복원** → EC2→Oracle 이전 그대로 통용). §9.7 주 1회 덤프가 이관 파일도 겸함
- **로컬 크롤링 → 서버 반영 경로 (원샷 클린 로드, §4)**: 로컬 `mysqldump` → **go-live 직전 빈 EC2 DB에 1회 적재**(중복키 dedup 로직 불필요). 업로드 시 `WHERE tier IN(...)`로 타깃 슬라이스(EC2=Master+). 인프라 조기 배포(§9.6) 후 스택 검증 중 EC2 DB에 데이터가 쌓였을 수 있으므로 **적재 직전 `TRUNCATE`(또는 `--replace`)로 "빈 DB" 전제 보증**. named volume은 로컬 디스크에만 있어 이 덤프가 유일한 이동 수단
- **prod compose 보안 (로컬 compose와 분리)**: ① DB/Redis 포트를 호스트·보안그룹에 **공개 금지** — 앱 컨테이너만 compose 내부 네트워크로 접근(로컬의 `3307:3306`/`6379:6379` 노출은 prod에서 제거) ② 비밀번호는 `.env` 주입, 로컬의 약한 비번 재사용 금지(§9.5). 약한 비번 + 포트 노출 결합은 공개 서버 침해의 대표 경로

---

## 10. 예상 작업 일정

**마감 목표**: 2026년 8월 31일
**투입 가능 시간**: 평일 매일 2h(고정) + 주말 이틀 각 7~8h → 주당 약 24~26h, 5주 합계 약 125~130h

### Phase별 추정 (확정)

| Phase | 내용 | 추정 | 비고 |
|---|---|---|---|
| Phase 1 | 세팅 + Riot API 연동 + DB 캐시 + 검색/자동완성 + 매치 조회(동기 최소 수집) + ddragon 아이콘 연동 + 화면 + 테스트 | **34~38h** | ddragon 연동(버전/매핑 캐싱 + 아이콘 표시) 포함 |
| Phase 2 | Bucket4j 전역 리미터 + 백그라운드 수집 큐 + [전적 갱신] + per-IP 제한 + Redis + 429 재시도 + 테스트 | **31~37h** | **전체에서 가장 무거운 Phase** |
| Phase 3 | 통계 집계(실시간, 배치 테이블 없음) + 티어 이력 + 화면 + 테스트 | **22~26h** | "DB 누적 기준" 스코프로 추가 수집 로직 불필요 |
| 배포 | EC2/도메인/HTTPS 수동 배포(3주차) + Personal Key 신청 + CI/CD(5주차) | **7~9h** | |
| Phase 5 | JWT 로그인 + 즐겨찾기 + 마이페이지 + Validation + 커버리지 보강 + Swagger + README | **18~22h** | 이메일 인증·재설정·잠금은 기본 이월(여유 시 복귀) |
| **소계 (Phase 4 제외)** | | **112~132h** | 가용 125~130h — 중앙값은 가용 내, **상단 시나리오(132h)는 가용 상단(130h) 소폭 초과** → go/no-go 수치 기준으로 통제 |
| Phase 4 | 차별화 최소 버전 (조건부 — 기본 이월) | 12~14h | go 판정 시에만 |

> 여유가 생기면 이월 항목 중 이메일 플로우부터 복귀.

### 실측 트래킹 (추정 대비 실제 — 완료 시마다 기록)

> 위 추정은 "Claude Code와 협업"(§1)을 명목으로 했지만 실질은 사람이 직접 구현하는 속도 기준이었다. 배율은 작업 유형마다 다르다(코드 생성형 작업은 크게 단축, 수집 큐 실동작 검증·배포·DNS 등 벽시계 시간이 지배하는 작업은 단축 폭 작음). **Phase 2까지 07/30에 완료 — 계획 캘린더(3주차) 대비 ~2주 선행.**
>
> **리드 사용처 (grilling 2026-07-30 확정 = C안)**: 일정 단축이 아니라 ① **소화 패스(§10 아래 — 학습의 주역)** ② **시드 크롤러 1개 추가**(§4). Phase 4·이메일 플로우는 이월 유지. 크롤러는 먼저 빌드·무인 가동, 사람 시간은 소화 패스에 투입(둘은 리드를 두고 경쟁 안 함).

| 항목 | 추정 | 실측 | 완료일 | 메모 |
|---|---|---|---|---|
| Phase 1 백엔드 (Riot 연동+DB 캐시+검색/자동완성+매치 조회) | ~20h | **4h** | 07/29 | 약 5배 단축 — 코드 생성형 작업 |
| Phase 1 화면+ddragon 표시+테스트 | 10~14h | **완료** | 07/30 | main/profile/match-detail 3화면 + ddragon 연동. 티어 엠블럼 자산만 잔여 |
| Phase 2 (전역리미터+수집큐+갱신+per-IP+Redis+재시도) | 31~37h | **완료** | 07/30 | ~2주 선행 |
| Phase 3 | 22~26h | | | |
| 배포(인프라 조기, §9.6) | 4~5h | | | 외부 대기 시간 지배 — 단축 기대 낮음 |
| Phase 5 | 18~22h | | | |
| CI/CD(5주차) | 2~3h | | | |

### 소화 패스 — 리드의 학습 주역 (★ grilling 2026-07-30)

> **왜 필요**: Phase 1·2는 TDD로 했지만 **테스트도 agent가 작성** → "테스트가 있다 ≠ 그 테스트가 내 이해"다. 목표가 백엔드 숙달(§1·§2)이므로 여기가 알맹이. 가벼운 보강이 아니라 진짜 소화가 필요(본인 확인).

- [ ] **(척추) Rebuild-from-tests** — 로직 무거운 유닛 2~3개(소환사 조회·매치 수집 등): ① 테스트만 읽어 계약 정리 → ② 기존 구현 가림(`git stash`/스크래치 클래스) → ③ **안 보고 직접 재구현**해 기존 테스트 통과 → ④ 원본과 **diff**. 갈라진 곳 = 몰랐던 곳. 테스트 통과했는데 분기를 빠뜨렸으면 **이해+커버리지 이중 구멍**(테스트 추가). 끝나면 원본으로 되돌림 — 목적은 코드가 아니라 강제 추론+diff
- [ ] **(선행 스캔) Explain 패스** — 각 서비스를 분기별 평어로 설명 후 코드 대조 (재구현 전 워밍업)
- [ ] **(선택·기계적) PITest 뮤테이션 테스트** — 살아남는 mutant = 테스트·이해 둘 다 약한 곳. JaCoCo(빈 분기)보다 강하게 약점 지목
- [ ] 소화 중 발견한 적대적 케이스는 그 자리에서 테스트로 추가(429/404/403/타임아웃/깨진JSON + 경계 입력 — §4 방법)

### Phase 4 이월 확정 (★ grilling 2026-07-30 — 기존 go/no-go 폐기)

기존 "3주차 go/no-go 판단"은 폐기한다. Phase 2를 07/30(1주차)에 끝내 ~2주 선행했으나, **리드를 기능 확장이 아니라 소화 패스 + 크롤러에 쓰기로(C안) 확정**했으므로 Phase 4(포지션별 상대전적)는 **마감 후 이월로 고정**한다. 판단 조건을 걸 이유가 없어졌다.
- Phase 4는 §10 "마감 이후 이월 항목"으로 이동 — 되살릴 경우의 **표본 데이터는 시드 크롤러(§4)가 이미 확보**해 두므로, 마감 후 착수 시 순수 집계 쿼리+화면만 남음
- 이메일 인증·재설정·로그인 잠금도 이월 유지(여유 시 이메일 플로우부터 복귀)

### 재작성 캘린더 (07/30 실제 진도 기준)

> **현재 위치(07/30)**: Phase 1 백엔드 + Phase 2 완료. 마감(08/31)까지 ~4.5주 × 24~26h ≈ **가용 110~120h**, 잔여 작업(아래) 대비 **큰 여유**. 슬랙은 소화 패스 깊이·README 완성도에 투입.
> **크롤러는 1주차부터 무인 상시 가동** — 아래 표와 병행해 백그라운드로 데이터 축적, 마감 주 원샷 로드까지 지속(§4).
> ※ **Phase 1 화면 완료 확인됨(repo kwakmunseok/lolstats)** — main/profile/match-detail 3화면 실서버렌더(th:each·th:src·검색/자동완성 인라인 JS) + ddragon 연동 완비. **유일한 꼬리 = 티어 엠블럼 정적 자산 미커밋**(static 디렉토리 없음) — 엠블럼 이미지 사용 시에만 추가, 텍스트/뱃지 표시면 불필요.

| 주차 | 기간 | 작업 | 비고 |
|---|---|---|---|
| 1주차 잔여 | 07/30–08/03 | **시드 크롤러 빌드(TDD)·가동 시작**(4~6h) + **소화 패스 착수**(rebuild-from-tests) + 티어 엠블럼 자산(선택) | Phase 1·2 완료 확인됨. 크롤러 이후 무인 가동 |
| 2주차 | 08/04–08/10 | 소화 패스 계속 + **Phase 3(통계/티어이력)** 착수 + **인프라 조기 배포**(빈 스택, URL 비공개, §9.6) | 배포 리스크 조기 소진 |
| 3주차 | 08/11–08/17 | Phase 3 마무리 + **Phase 5** 착수(JWT 로그인·즐겨찾기) | |
| 4주차 | 08/18–08/24 | Phase 5 마무리(마이페이지·Validation·Swagger) + **CI/CD** | |
| 5주차 | 08/25–08/31 | README(ERD·아키텍처·트러블슈팅·URL) + **크롤 데이터 원샷 로드**(빈 EC2 DB TRUNCATE→Master+ 슬라이스) + **도메인 연결 → go-live** + 버퍼 | 여유 시 이메일 플로우 복귀 |

**목표 완료 시점: 2026년 8월 31일**

> 완성된 기본기 + 백그라운드 수집 아키텍처 + 시드 크롤러 예열 데이터 + 실서비스 URL + CI/CD + 소화로 다진 이해 — 핵심 학습 목표(§2) 달성.

### 개발 외 설정 작업 (남는 시간에 앞당겨 처리)

코딩 외의 계정·인프라·설정 작업. **A를 미리 끝내면 3주차 배포일(4~5h)이 순수 서버 작업만 남는다.**

**A. 지금 바로 가능 (총 ~2.5h)**
- [ ] GitHub repo 생성 + 첫 push — push 전 `git ls-files | grep -i "\.env"` / `git log --oneline --all -- .env` 둘 다 출력 없음 확인, `.env.example`(키 이름만) 추가
- [x] ~~Riot Personal API Key 신청~~ **완료·승인됨(07/30)** — repo URL 포함 신청, 승인 완료. 키 위치는 포털 대시보드 홈의 Development Key가 아니라 **계정 메뉴 → APPS → 앱(LoL Stats) 상세 페이지**(24h 만료 없는 상시 키). `.env`에 적용하면 Dev Key 24h 갱신 종료
- [ ] 도메인 구매 (DNS 설정은 EC2 IP 나온 뒤)
- [ ] AWS 계정 + EC2 프리티어 인스턴스 생성만 (보안그룹은 SSH 22를 내 IP로만)
- [ ] 티어 엠블럼 자산 다운로드 (Riot 포털 "Ranked Emblems" — ddragon에 없음)
- [ ] **Oracle 가입 + A1 인스턴스 확보 시도(집에서, 병렬)** — 가입 시 결제수단(해외결제 카드) 등록 필수 → 집에서 진행. 홈 리전 **Tokyo**, "Always Free eligible" Shape만 선택. 용량 잡힐 때까지 생성만 재시도(세팅은 마감 후, §9.1)
- [ ] (루틴) Dev Key 24h 갱신 — Personal Key 승인 시 종료

**B. 3주차 수동 배포 시점 (§9.6)**
- [ ] EC2 세팅: Docker/Compose, swap 2GB, 보안그룹 80/443 오픈
- [ ] **탄력적 IP(EIP) 할당** 후 DNS A레코드 연결 (재부팅 시 IP 변경으로 DNS 깨짐 방지)
- [ ] Nginx + Let's Encrypt(certbot) + 자동갱신 cron — **프록시 헤더 필수**(§9.2)
- [ ] 서버 `.env` 작성 + compose `env_file` 주입 확인 (약한 로컬 비번 재사용 금지 — §9.8)
- [ ] **prod compose 분리**: MySQL named volume 유지 + DB/Redis `ports:` 제거(외부 비공개, 보안그룹 미개방) — §9.8
- [ ] Redis `maxmemory 64mb`/`allkeys-lru` + JVM `-Xmx384m` (§8)
- [ ] 배포 후: UptimeRobot 등록, mysqldump 주 1회 cron (§9.7)

**C. 5주차 CI/CD 시점 (§9.4)**
- [ ] GitHub PAT 발급(`write:packages`) + repo Secrets 등록(EC2 SSH 키, GHCR 토큰)
- [ ] EC2에서 `docker login ghcr.io`

**D. 조건부 — 이메일 플로우 복귀 시에만 (§4 Phase 5)**
- [ ] Gmail 2단계 인증 + 앱 비밀번호 발급

### 일정 압박 시 감축 순서 (미리 합의해두는 우선순위)

1. Phase 4 전체 이월 (기본값)
2. 이메일 인증·재발송·비밀번호 재설정·로그인 잠금 이월 (기본 이월 — 여유 시에만 복귀)
3. Phase 5 커버리지 보강 축소 (각 Phase에서 작성한 테스트까지만)
4. 챔피언 통계 화면 간소화 (탭 1개로 축소)
5. **끝까지 지키는 것**: JWT 로그인+즐겨찾기, 실서비스 URL, CI/CD, README — 이 4개가 핵심 목표(§2)의 뼈대라 빠지면 프로젝트 의미가 약해짐

### 마감 이후 이월 항목 (여유 생기면 확장)
- Phase 4 차별화 기능 (no-go 판정 시 전체 이월) — 듀오 시너지 분석, 룬/빌드 추천
- **매치 타임라인** — match-v5 timeline은 **별도 엔드포인트라 매치당 +1 호출**(신규 소환사 1명 24회→44회). §4 호출 예산에 반영된 적 없는 스코프 크리프 씨앗이라 MVP에서 명시 제외
- **Bucket4j Redis 백엔드 전환** — 앱 인스턴스가 1대라 in-memory로 충분. 다중 인스턴스로 확장할 때만 도입
- **자동완성 Redis 캐시** — 미채용 근거: 자동완성 대상이 "과거 검색된 소환사"뿐이라 데이터가 수천 행 이하이고, `game_name` 인덱스 + LIKE 'q%'는 이 규모에서 밀리초 수준. 캐시 계층을 얹어도 체감 이득이 없고 무효화 관리(신규 소환사 저장 시 prefix 키 갱신) 비용만 생김. 누적 데이터 증가로 자동완성 응답이 실측 지연될 때 도입 — 이 판단 근거("왜 Redis를 안 썼나")를 README 트러블슈팅에 기록
- 테스트 커버리지 확대, README 트러블슈팅 상세 보강
- **화면 커스텀 스타일링 (Bootstrap 기본 룩에서 브랜딩 강화) — 도구: Hallmark**
  - Hallmark = AI 티 안 나는 디자인 스킬(Nutlope/hallmark, MIT, `npx skills add nutlope/hallmark`). 자체 완결형 HTML+커스텀 CSS 산출, 동사 4개(create/audit/redesign/study)
  - **Thymeleaf와 공존 가능** — 다른 레이어(Thymeleaf=데이터 바인딩 / Hallmark=마크업·CSS). Hallmark 정적 HTML에 `th:*` 속성만 얹으면 됨(Thymeleaf natural templating이 목업 소비에 최적). 비용은 "정적 HTML→Thymeleaf 조각 변환"이라는 일상 작업뿐, 호환성 문제 아님
  - **진짜 결정은 화면 단위 Bootstrap↔커스텀 CSS 택일** — 두 스타일 시스템을 한 화면에 겹치면 충돌, 그래서 화면별로 Bootstrap 유지 vs 커스텀 전환을 고름(Thymeleaf는 어느 쪽이든 그대로 동작)
  - 고도화 로드맵: ① 전 화면 `hallmark audit`(코드 수정 없이 지적 목록 — 리스크 0) → ② 메인/검색(#1)부터 `study`(op.gg DNA 추출, 픽셀복제 거부)+`redesign`으로 커스텀 전환(효과 최대·데이터 밀집 아님) → ③ 프로필·매치 상세(#2·#3)는 데이터 밀집 앱 UI라 정보설계 중심이므로 audit 결과를 Bootstrap 위에 반영하거나 여력 시 순차 전환 → ④ 커스텀 전환 화면은 Thymeleaf 이식 비용을 작업에 포함

---

## 11. 다음 액션

1. Riot Developer Portal에서 Development API Key 발급 + **Personal Key 즉시 신청**(검증 절차 없이 상세 설명만 필요 — 리뷰 대기 선행, 승인 전까진 Dev Key로 개발. §9.6·아래 표)
2. 도메인 구매 + AWS 계정/EC2 프리티어 인스턴스 생성 (세팅은 3주차, 생성만 미리)
3. Spring Boot 프로젝트 초기 세팅 (build.gradle, 기본 구조, Thymeleaf + Bootstrap 5 CDN 연결)
4. Phase 1의 "소환사 검색 API"부터 Claude Code와 함께 구현 시작 (DB 캐시 + 동기 수집 최소화 포함, §4 아키텍처 원칙 준수)

### API Key 전략

| 구분 | 대상 | 이 프로젝트 |
|---|---|---|
| Development Key | 테스트/개발 중 | **지금~배포 전** 사용. 24h 만료 → 매일 갱신 |
| Personal Key | 개인/소규모 프로젝트 | **즉시 신청(1주차)** — 무료, 24h 만료 없음. 검증 절차 없이 상세 텍스트 설명만 요구(동작 화면·스크린샷 불필요). rate limit은 Dev와 동일(제약 영구) |
| Production Key | 실사용자 있는 대규모 서비스 | **신청 안 함** — 동작하는 프로토타입 요구, 개인 학습 프로젝트엔 신청 사유가 약함 |

※ Riot API는 등급 무관 완전 무료 (결제 수단 등록 자체가 없음)
※ **Personal Key 공개 서비스 제한 (인지된 선택)**: 공식 정책상 personal key로 "public consumption"(오픈 알파/베타 포함) 운영 금지 — 용도는 개발자 본인/소규모 사적 커뮤니티까지. 본 프로젝트의 배포 URL은 학습·본인 확인용으로 유지하고 **불특정 다수 대상 홍보는 하지 않아** 개인 사용 범주를 지킨다. 공개 서비스로 전환하려면 Production Key 필요 — README에 기록

---

## 12. Claude Code 활용 팁

- 이 문서를 프로젝트 루트에 `PROJECT_PLAN.md`로 저장해두고, Claude Code 세션 시작 시 참조하도록 안내
- Phase 단위로 작업을 쪼개서 요청 (한 번에 전체를 요청하지 않기)
- 각 Phase 완료 후 README의 "트러블슈팅" 섹션에 겪은 문제와 해결 과정을 기록 (학습 내용을 남기는 핵심 장치)
- 테스트 코드는 기능 구현과 같은 세션에서 바로 작성 요청 (본 계획서는 아예 각 Phase 항목에 테스트를 포함시킴)
