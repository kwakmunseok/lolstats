# 롤(LoL) 전적 검색 사이트 - 프로젝트 계획서 (v3.0)

> v2.x 리뷰 이력을 걷어낸 확정판. 변경 이력은 git이 담당한다.
> 프로젝트 루트에 `PROJECT_PLAN.md`로 저장해 Claude Code 세션 컨텍스트로 사용.

## 1. 프로젝트 개요

| 항목 | 내용 |
|---|---|
| 프로젝트명 | (가칭) LoL Stats / 추후 확정 |
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
| 통계 데이터 부족 (서비스 초기 DB 누적 매치가 적음) | 통계 스코프를 "DB 누적 기준"으로 정의하고 화면에 "N게임 기준" 표기. README에 설계 근거 명시 |
| Riot API 응답 스키마 변경 | DTO 계층 분리, API 버전 관리 |
| LoL 패치 후 아이콘 깨짐 (신규 챔피언/아이템) | ddragon 버전 하드코딩 금지 — versions.json 최신값 캐싱(하루 1회 갱신) 사용 (§4 설계 노트) |
| 데이터량 증가로 DB 성능 저하 | 인덱스 설계(MATCH_PARTICIPANTS.puuid 등), 배치 집계 테이블 분리 |
| EC2 프리티어 메모리 부족 (1GB에 app+MySQL+Redis) | swap 2GB 설정 + JVM 힙 제한(`-Xmx384m` 수준) + Redis `maxmemory 64mb` + `maxmemory-policy allkeys-lru` 설정(용도가 전부 소용량 키라 충분). 그래도 부족하면 §9 대안(Oracle Cloud) 전환 |
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

- **도메인**: 가비아/Cloudflare에서 `.com`/`.kr` 구매 (연 ₩13,000~20,000 — 이 프로젝트의 유일한 고정 비용). 무료로 하려면 DuckDNS 서브도메인도 가능하지만, 도메인 등록·DNS 설정 경험을 위해 자체 도메인 권장.
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
| 3주차 | EC2 세팅(Docker, swap) + Nginx/HTTPS + 수동 배포 | 4~5h |
| 5주차 | GitHub Actions CI/CD 완성 | 2~3h |

### 9.7 운영 최소한

- 로그: `docker compose logs` + Spring Boot 파일 로그면 충분 (ELK 등은 오버엔지니어링)
- 모니터링: UptimeRobot 무료 플랜으로 다운 감지 알림 정도만
- 백업: MySQL 볼륨 주 1회 `mysqldump` cron — 학습 프로젝트 데이터라 이 이상은 불필요

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

> 위 추정은 "Claude Code와 협업"(§1)을 명목으로 했지만 실질은 사람이 직접 구현하는 속도 기준이었다. Phase 1 백엔드 실측에서 약 5배 차이 확인 — 단, 배율은 작업 유형마다 다르다(코드 생성형 작업은 크게 단축, 수집 큐 실동작 검증·배포·DNS 등 벽시계 시간이 지배하는 작업은 단축 폭 작음). **전체 일정의 재산정은 Phase 2 실측까지 확보한 뒤에 한다.** 앞당겨진 여유는 일정 단축이 아니라 ① 생성 코드 소화(직접 읽고 설명 가능한 수준) ② 테스트 직접 작성 ③ 이월 항목 복귀(이메일 플로우 → Phase 4 순)에 쓴다 — 목적이 학습(§1)이므로.

| 항목 | 추정 | 실측 | 완료일 | 메모 |
|---|---|---|---|---|
| Phase 1 백엔드 (Riot 연동+DB 캐시+검색/자동완성+매치 조회) | ~20h | **4h** | 07/29 | 약 5배 단축 — 코드 생성형 작업 |
| Phase 1 화면+ddragon 표시+테스트 | 10~14h | | | |
| Phase 2 | 31~37h | | | 재산정 기준점 — 실동작 검증 포함 실측 |
| Phase 3 | 22~26h | | | |
| 배포(수동, 3주차) | 4~5h | | | 외부 대기 시간 지배 — 단축 기대 낮음 |
| Phase 5 | 18~22h | | | |
| CI/CD(5주차) | 2~3h | | | |

### Phase 4 go/no-go 기준 (3주차 일요일, 08/17 판단)

3주차 종료 시점에 아래 **세 가지가 모두** 충족되면 go, 하나라도 미달이면 no-go(전체 이월):
1. Phase 2 완료 (백그라운드 큐 + Bucket4j 동작 확인 포함) — 실소요 35h 이내
2. 수동 배포 완료 (HTTPS URL로 접속 가능한 상태)
3. 누적 실소요가 계획 대비 +5h 이내 (1~3주차 계획 약 77h 기준, 실소요 82h 이내)

### 확정 캘린더

| 주차 | 기간 | 작업 | 주간 투입 |
|---|---|---|---|
| 1주차 | 07/28–08/03 | Phase 1 (~24h 진행) + 도메인/EC2 인스턴스만 생성 + Personal Key 신청(1~2h) | ≈ 25h |
| 2주차 | 08/04–08/10 | Phase 1 마무리(10~14h, ddragon 아이콘 포함) + Phase 2 착수: Bucket4j + 429 재시도부터(12~16h) | ≈ 26h |
| 3주차 | 08/11–08/17 | Phase 2 마무리: 수집 큐 + Redis(15~17h) + **수동 배포(4~5h)** + Phase 3 착수(4~6h) → **일요일: go/no-go 판단** | ≈ 26h |
| 4주차 | 08/18–08/24 | Phase 3 마무리(19~23h) + Phase 5 인증 파트 착수(3~7h) | ≈ 26h |
| 5주차 | 08/25–08/31 | Phase 5 마무리(JWT/Validation/Swagger/README — 이메일 플로우는 여유 시에만) + CI/CD(2~3h) + (go 판정 시에만) Phase 4 | ≈ 26h + 주말 버퍼 1~2h |

**목표 완료 시점: 2026년 8월 31일**

> no-go여도 "완성된 기본기 + 백그라운드 수집 아키텍처 + 실서비스 URL + CI/CD"가 남으므로 핵심 학습 목표는 달성됨.

### 개발 외 설정 작업 (남는 시간에 앞당겨 처리)

코딩 외의 계정·인프라·설정 작업. **A를 미리 끝내면 3주차 배포일(4~5h)이 순수 서버 작업만 남는다.**

**A. 지금 바로 가능 (총 ~2.5h)**
- [ ] GitHub repo 생성 + 첫 push — push 전 `git ls-files | grep -i "\.env"` / `git log --oneline --all -- .env` 둘 다 출력 없음 확인, `.env.example`(키 이름만) 추가
- [ ] Riot Personal API Key 신청 (repo URL 포함 — §11) — 승인 대기 시작이 빠를수록 좋음
- [ ] 도메인 구매 (DNS 설정은 EC2 IP 나온 뒤)
- [ ] AWS 계정 + EC2 프리티어 인스턴스 생성만 (보안그룹은 SSH 22를 내 IP로만)
- [ ] 티어 엠블럼 자산 다운로드 (Riot 포털 "Ranked Emblems" — ddragon에 없음)
- [ ] (루틴) Dev Key 24h 갱신 — Personal Key 승인 시 종료

**B. 3주차 수동 배포 시점 (§9.6)**
- [ ] EC2 세팅: Docker/Compose, swap 2GB, 보안그룹 80/443 오픈
- [ ] **탄력적 IP(EIP) 할당** 후 DNS A레코드 연결 (재부팅 시 IP 변경으로 DNS 깨짐 방지)
- [ ] Nginx + Let's Encrypt(certbot) + 자동갱신 cron — **프록시 헤더 필수**(§9.2)
- [ ] 서버 `.env` 작성 + compose `env_file` 주입 확인
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
- 화면 커스텀 스타일링 (Bootstrap 기본 룩에서 브랜딩 강화)

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
