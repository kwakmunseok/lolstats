# Phase 5 상세 작업 계획서 (인증/마무리)

> [PROJECT_PLAN.md](./PROJECT_PLAN.md) §4 Phase 5, §5 화면 목록, §6 회원/토큰 데이터 모델, §7 Auth·마이페이지 API를 실행 단위로 쪼갠 작업 분해서(WBS). [PHASE1_PLAN.md](./PHASE1_PLAN.md)~[PHASE3_PLAN.md](./PHASE3_PLAN.md)와 동일한 형식.
> 예산: **18~24h**(Track A, Task 1~8 + Task 10 — PROJECT_PLAN.md §10의 18~22h보다 다소 높음. Swagger가 Phase 1~3 기존 엔드포인트까지 소급 적용되고 README가 3개 Phase분 트러블슈팅을 정리해야 해서). **Task 9(CI/CD)는 별도** — §10 "배포" 라인(7~9h)에서 카운트, 진행상황만 이 문서에서 같이 추적.
> **상태: 미착수 — 이 문서가 최초 계획서.** Phase 4는 마감 후 이월 확정(§10, 07/30 grilling)이라 건너뛰고 이 문서로 진행.

## 0. 착수 전 확인 사항 — 블로커

- [ ] **2주차 몫이었던 "인프라 조기 배포"(§9.6, 4~5h)가 아직 안 됨** — `.github/workflows` 없음, `Dockerfile` 없음, 배포 관련 커밋 없음(확인일 2026-08-08). §9.6이 정확히 경고한 상황("Phase 5 마지막 주에 처음 시도하면 서버 세팅 문제가 마감 직전에 터진다")이 이미 2주 밀림. 오늘(08/08) 기준 마감(08/31)까지 ~3.5주 남아 아직 여유는 있지만, 이 문서의 **Track B(Task 9 CI/CD, Task 10의 서비스 URL)는 인프라 배포 없이는 시작 자체가 불가** — §10 "일정 압박 시 끝까지 지키는 것" 4개 중 2개(실서비스 URL, CI/CD)가 여기 걸려있음
- [ ] §10 "개발 외 설정 작업 A" 미완료 항목(도메인 구매, AWS 계정+EC2 인스턴스 생성)도 같은 이유로 우선 트리거 권장 — 둘 다 외부 대기시간(DNS 전파, 결제 승인 등)이 있어 먼저 걸어두는 게 유리, 압축 안 됨
- [ ] Track A(Task 1~8, 아래 §2)는 인프라와 무관하게 지금 바로 착수 가능 — 순서 안 막힘

## 0.1 진행 현황 & 재개 방법

- Phase 1~3 완료(Phase 3: 2026-08-08, `PHASE3_PLAN.md` 참고). Phase 4는 마감 후 이월 확정.
- 다음 세션 시작 시: Track A Task 1(USERS 엔티티 + 회원가입)부터 순서대로. 인프라 배포(도메인 구매·EC2 생성)는 Claude Code 범위 밖이라 사용자가 별도로 트리거해야 함 — §0 블로커 참고.

## 1. 이 문서 범위에 포함되지 않는 것

- 이메일 인증/재발송/비밀번호 재설정/로그인 잠금 — **기본 이월**(PROJECT_PLAN.md §4 Phase 5, §10 감축 순서 2번). `EMAIL_VERIFICATION_TOKENS`/`PASSWORD_RESET_TOKENS` 테이블도 함께 이월 — 가입 즉시 계정 활성화로 진행
- Phase 4 상대 챔피언 승률 — 마감 후 이월 확정(§10)
- EC2/도메인/HTTPS 서버 세팅 자체(§9.1~9.3, §9.6) — 이 문서 밖 별도 인프라 트랙(§0 블로커 참고). 이 문서는 그 위에서 도는 CI/CD 파이프라인(Task 9)만 다룸

## 2. 작업 순서 (의존성 기준)

두 트랙이 독립적으로 진행된다 — Track A는 지금 바로, Track B는 인프라 배포가 끝나야 시작된다.

```
Track A — 앱 레이어 (인프라 무관, 즉시 착수 가능)

Task 1: USERS 엔티티 + 회원가입
(bcrypt 해싱, 약관동의, 가입 즉시 활성)
        │
        ▼
Task 2: JWT 로그인/로그아웃/재발급
(httpOnly 쿠키, REFRESH_TOKENS 해시 저장, Spring Security 필터)
        │
   ┌────┴──────────────┐
   ▼                    ▼
Task 3: 즐겨찾기 API      Task 4: 최근 검색 기록 저장/조회
   └────────┬───────────┘
            ▼
Task 6: 전 폼 Validation 통합 (순서 변경 — Task 5보다 먼저.
         로그인/가입 폼이 필드별 에러 메시지에 의존해서 먼저 해야
         화면 JS를 두 번 안 건드림, advisor 조언 반영)
            │
            ▼
Task 5: 로그인/회원가입/마이페이지 화면
            │
            ▼
Task 7: 테스트 커버리지 보강
            │
            ▼
Task 8: Swagger API 문서화

Track B — 인프라 게이트 (§0 블로커 해소 후에만 착수 가능)

(인프라 조기 배포 — 이 문서 밖, 사용자가 별도 진행)
            │
            ▼
Task 9: CI/CD 파이프라인 완성 (§9.4)
            │
            ▼
Task 10: README 작성 (Track A 산출물 + Track B의 서비스 URL 둘 다 필요 — 두 트랙 수렴 지점)
```

## 3. 상세 작업 항목 (WBS)

### Track A

#### 1. USERS 엔티티 + 회원가입 — 2~3h ✅ 완료

- [x] `USERS` 엔티티(PROJECT_PLAN.md §6: id, email unique, password_hash, nickname, email_verified, login_fail_count, locked_until, created_at) — 이메일 인증/잠금이 이월이라 `email_verified`는 가입 시 즉시 true로 세팅, `login_fail_count`/`locked_until` 컬럼은 스키마 그대로 남기되 로직은 미연결(이월 시 스펙대로). `ddl-auto: update`로 자동 생성 확인(`bit(1)`/`varchar(255)` 등 Hibernate 기본 매핑 그대로)
- [x] `POST /api/auth/signup` — 이메일/비밀번호/닉네임/약관동의 필수, `BCryptPasswordEncoder`로 해싱(`spring-security-crypto`만 추가 — `spring-boot-starter-security` 전체는 Task 2에서 붙임, 안 그러면 기존 Phase 1~3 공개 엔드포인트가 전부 잠김), 이메일 중복 체크
- [x] 테스트: 정상 가입(해싱 확인 — `AuthServiceTest`), 이메일 중복 409, 약관 미동의 400, 이메일 형식 오류 400
- [x] **발견(Task 6에 영향)**: `@Valid @RequestBody` 검증 실패(약관 미동의, 이메일 형식 오류)가 **커스텀 핸들러 없이도** `mvc.problemdetails.enabled=true`만으로 이미 400 ProblemDetail로 정규화됨(라이브 확인). 즉 Task 6의 "`ApiExceptionHandler`에 `MethodArgumentNotValidException` 핸들러 추가"는 불필요 — 다만 `detail` 필드가 필드별 메시지가 아니라 제네릭 "Invalid request content."로만 나와서, 폼에 구체적 에러 메시지를 보여주려면 그 부분만 손볼 필요는 있음(Task 6에서 판단)

**완료 기준 — 확인됨**: 라이브 회원가입 → `201` + `{"id":1,"email":"...","nickname":"테스터"}`(비밀번호 필드 없음 확인). 같은 이메일 재가입 → `409`. 약관 미동의/이메일 형식 오류 → 둘 다 `400`. DB 직접 조회로 `password_hash`가 `$2a$10$...`(bcrypt) 형태로 저장, 평문 아님 확인. 유닛 테스트 2개(`AuthServiceTest`) + 전체 스위트 89개 통과.

#### 2. JWT 로그인/로그아웃/재발급 — 5~6h ✅ 완료

- [x] `REFRESH_TOKENS` 엔티티(§6) — `token_hash`로 저장(원문 저장 금지, SHA-256 — bcrypt는 저엔트로피 비밀번호용이라 이미 256비트 랜덤인 refresh 토큰엔 과함), `revoked` 플래그로 무효화
- [x] `POST /api/auth/login` — 이메일/비번 검증 후 access(30분)/refresh(14일, §5 미결 기본값 채택) 토큰을 httpOnly 쿠키로 응답(§6 확정 — Thymeleaf 서버 렌더 페이지 이동엔 Authorization 헤더를 못 붙이고, localStorage 저장은 XSS에 취약). `Secure` 플래그는 `app.jwt.cookie-secure`로 프로필별 분리(dev=false, prod=true — 안 그러면 로컬 http에서 로그인이 "성공"해도 쿠키가 전송 안 돼 다음 요청부터 익명 처리되는 유령 버그). 5회 연속 실패 잠금은 이월(Task 1 참고)이라 미구현
- [x] `POST /api/auth/logout` — refresh token revoke + 쿠키 삭제
- [x] `POST /api/auth/refresh` — refresh 쿠키 검증(해시 대조 + 만료/revoked 체크) 후 access 재발급. 회전(rotation) 없음(§5 미결 기본값)
- [x] `spring-boot-starter-security` 추가(Task 1의 `spring-security-crypto` 단독 의존성을 대체) + `JwtAuthenticationFilter`(쿠키의 JWT 파싱해 `SecurityContextHolder`에 userId principal만 세팅 — `UserDetailsService`/권한 불필요, "유효한 세션인가"만 확인하면 됨) + `SecurityConfig`에 `SecurityFilterChain` 빈 추가
- [x] **CSRF는 이번 Task 범위에서 제외, Task 3로 미룸(계획과 다른 결정 — 아래 §5 미결 참고)**: `CookieCsrfTokenRepository` + 커스텀 토큰-강제-resolve 필터로 구현 시도했으나 라이브 검증 중 XSRF-TOKEN 쿠키가 요청마다 지워졌다 재생성됐다 하는 문제 발견(`request.getAttribute("_csrf")` 대신 `CsrfToken.class.getName()`을 읽은 게 원인 — Spring의 `XorCsrfTokenRequestAttributeHandler` BREACH 방어 인코딩을 우회해버림). 키를 고쳐도 여전히 매끄럽지 않았고, **지금 시점엔 CSRF로 보호할 상태 변경 엔드포인트가 사실상 없음**(signup/login=세션 자체가 없음, logout/refresh=공격자가 피해자 쿠키를 훔쳤다면 이미 그걸로 직접 호출 가능이라 CSRF로 막을 추가 이득이 없음, 기존 `/api/summoners/{id}/refresh`=개인 상태 변경 아님)이 결론이라 `csrf(disable)`로 전환. Task 3의 `/api/users/me/**`(즐겨찾기)가 처음으로 "진짜 개인 상태를 바꾸는" 엔드포인트라 CSRF 토큰은 그때 화면과 같이 넣음
- [x] 테스트: `JwtTokenServiceTest`(5개 — 토큰 발급/파싱 round-trip, 변조 거부, 쓰레기 입력 거부, 해시 일관성, opaque 토큰 랜덤성), `AuthServiceTest`(9개 신규 — 로그인 성공/오탈자 비번/미가입 이메일, 재발급 성공/revoked/만료/미존재 토큰, 로그아웃 성공/미존재 토큰)

**완료 기준 — 확인됨(라이브, Node `http` 모듈로 쿠키 직접 검사)**: 회원가입 → 로그인(`200`, `access_token`+`refresh_token` 쿠키 확인 — `HttpOnly`/`SameSite=Lax`/`Max-Age` 정확, dev에선 `Secure` 없음 확인) → `POST /api/auth/refresh`(`200`, 새 access 토큰) → 로그아웃(`200`) → 로그아웃 후 재발급 시도(`401` + "다시 로그인해주세요" — DB에서 실제 revoke 확인) → 틀린 비번/미가입 이메일 로그인(둘 다 `401`, 응답 동일 — enumeration 방지) 전부 확인. 기존 Phase 1~3 공개 라우트(`/summoners/**`, `/api/summoners/**` GET/POST) 전부 무인증 `200` 유지 확인 — Spring Security 도입이 기존 기능을 잠그지 않았음. `/api/users/me/favorites`(Task 3 미구현) 무인증 호출 시 `401` 확인 — `authorizeHttpRequests` 매처가 아직 없는 라우트에도 먼저 적용됨. 유닛 테스트 14개 신규 + 전체 스위트 103개 통과.

#### 3. 즐겨찾기 API — 1.5~2h (+CSRF 이관분, 아래 참고) ✅ 완료

- [x] `FAVORITES` 엔티티(§6, `(user_id, summoner_id)` unique 제약)
- [x] `GET/POST /api/users/me/favorites`, `DELETE /api/users/me/favorites/{summonerId}` — 전부 JWT 필요(`@AuthenticationPrincipal Long userId` — `JwtAuthenticationFilter`가 세팅한 principal 그대로 사용, 별도 `UserDetailsService` 불필요)
- [x] 추가/삭제 둘 다 **멱등**으로 설계(추가 = 이미 있으면 무시, 삭제 = 없으면 무시) — 즐겨찾기 토글 버튼 UI에서 "이미 즐겨찾기됨" 에러를 별도로 처리할 필요가 없어짐
- [x] 존재하지 않는 summonerId로 추가 시도 시 404
- [x] 테스트: 추가/중복 추가(멱등 확인)/미존재 소환사 404/삭제/미존재 삭제(멱등 확인)/목록 조회 정렬 — `FavoriteServiceTest` 6개
- [x] **CSRF 토큰 구현(Task 2에서 이관됨, §5 미결 해소)**: Spring Security 자체 `CsrfFilter`(`CookieCsrfTokenRepository`) 대신 **직접 구현한 이중 제출 쿠키(double-submit-cookie) 필터**(`CsrfDoubleSubmitFilter`) 채택 — Task 2에서 겪은 XOR 인코딩/지연 로딩 문제를 피하려고 더 단순한 방식으로 전환. 로그인/재발급 시 `XSRF-TOKEN` 쿠키(httpOnly 아님 — JS가 읽어야 함)를 추가 발급하고, `/api/users/me/**`로 오는 상태 변경 요청(POST/PUT/PATCH/DELETE)에 대해 `X-XSRF-TOKEN` 헤더 값이 쿠키 값과 일치하는지만 검사. 서버 사이드 저장/만료 로직 없음(무상태) — 값 일치 여부만 확인하면 되는 게 이중 제출 쿠키 패턴의 핵심이라 그걸로 충분

**완료 기준 — 확인됨(라이브)**: 로그인 → `XSRF-TOKEN` 쿠키 발급 확인 → CSRF 헤더 없이 즐겨찾기 추가 시도 `403` → 헤더 포함하면 `201` → 같은 소환사 중복 추가해도 `201`(목록엔 1건만, 중복 없음 확인) → 존재하지 않는 소환사 추가 시도 `404` → CSRF 헤더 없이 삭제 `403` → 헤더 포함 삭제 `204` → 삭제 후 목록 조회 시 빈 배열 → 비로그인 상태 목록 조회 `401` → 기존 공개 라우트(프로필 화면, `/api/summoners/{id}/refresh` — `/api/users/me/**` 밖이라 CSRF 검사 대상 아님) 전부 정상. 유닛 테스트 6개 신규 + 전체 스위트 109개 통과.

#### 4. 최근 검색 기록 저장/조회 — 1.5~2h ✅ 완료

- [x] `SEARCH_HISTORY` 엔티티(§6)
- [x] `GET /api/users/me/search-history` — JWT 필요, 최근 20건(`PageRequest.of(0, 20)`)
- [x] **미결(§5) 해소 — "선택적 인증"은 이미 공짜로 있었음**: `JwtAuthenticationFilter`(Task 2)가 라우트 종류와 무관하게 **모든** 요청에서 돌면서 유효한 `access_token` 쿠키가 있으면 `SecurityContextHolder`에 principal을 세팅하는 구조라, `/api/summoners/riot-id/{gameName}/{tagLine}`(permitAll)에 `@AuthenticationPrincipal Long userId` 파라미터만 추가하면 로그인 여부에 따라 자동으로 null/실제 id가 들어옴 — 별도 분기 로직 불필요. 다만 이걸 위해 **`SecurityConfig`에서 익명 인증(anonymous auth)을 꺼야 했음**: 기본값(Spring Security anonymous 활성화)이면 비로그인 요청도 principal이 `"anonymousUser"`(String)인 `AnonymousAuthenticationToken`이 세팅돼 있어서 `@AuthenticationPrincipal Long userId`가 String→Long 캐스팅에서 죽음. `.anonymous(disable)`로 비로그인 시 `Authentication`이 아예 null이 되게 바꿈(`.authenticated()` 매처는 원래도 익명 토큰을 "인증 안 됨"으로 처리하고 있었어서 Task 2/3에서 검증한 401 동작엔 영향 없음 — 라이브로 재확인)
- [x] 검색 기록도 **멱등/중복 방지**: 같은 소환사를 다시 검색하면 새 행을 추가하는 대신 기존 행의 `searched_at`만 갱신(맨 위로 이동) — `profile.html`에 이미 있는 비로그인 사용자용 `recentSearches` localStorage 로직과 동일한 UX 패턴
- [x] 테스트: `SearchHistoryServiceTest` 3개(신규 검색 저장, 재검색 시 타임스탬프만 갱신, 목록 조회)

**완료 기준 — 확인됨(라이브)**: 비로그인 검색 `200`(에러 없음, 기록 안 됨) → 로그인 후 같은 소환사 검색 `200` → `/api/users/me/search-history` 조회 시 1건 확인 → 같은 소환사 재검색 후 다시 조회해도 여전히 1건(중복 없음, dedup 확인) → 비로그인으로 `/api/users/me/favorites`·`/api/users/me/search-history` 둘 다 여전히 `401`(익명 인증 비활성화가 기존 Task 2/3 동작을 깨지 않았음 확인) → Task 3의 즐겨찾기 추가(CSRF 헤더 포함)도 정상 동작 확인 → 공개 화면 라우트도 정상. 유닛 테스트 3개 신규 + 전체 스위트 112개 통과.

#### 5. 로그인/회원가입/마이페이지 화면 — 3~4h ✅ 완료

- [x] `login.html`/`signup.html` — Bootstrap 폼 컴포넌트 + vanilla JS + fetch(§3 화면 기술 선택 그대로). 회원가입 성공 시 이메일 인증 이월(§1)이라 곧바로 `/api/auth/login` 호출해 자동 로그인 처리
- [x] `mypage.html` — 즐겨찾기 목록(제거 버튼) + 최근 검색 기록, 둘 다 SSR(`PageController`가 `FavoriteService`/`SearchHistoryService`를 직접 호출 — Task 1~4에서 이미 검증된 API 응답 DTO `FavoriteResponse`/`SearchHistoryResponse`를 그대로 모델에 실음, 화면 전용 View 레코드 새로 안 만듦)
- [x] nav에 로그인 상태 표시 — `CurrentUserModelAdvice`(신규, `@ControllerAdvice(assignableTypes = PageController.class)`)가 `@AuthenticationPrincipal Long userId`로 매 PageController 요청마다 "currentUser" 모델 속성을 채움. API 컨트롤러엔 안 붙임(JSON 응답엔 의미 없고 요청마다 불필요한 DB 조회만 늘어남)
- [x] 프로필 화면에 즐겨찾기 토글 버튼 추가(로그인 시에만 노출) — `FavoriteService.isFavorited()` 신규 추가
- [x] 화면 검증은 Phase 3 Task 4 관행대로 로컬 Playwright로 직접 확인

**진행 중 발견/수정한 실제 버그 2건(계획엔 없던 것 — 라이브 검증이 아니었으면 못 잡았을 것들)**:
1. **`ApiExceptionHandler`가 실제로 안 불림**: Task 6에서 다룸(위 참고) — `@Order` 안 걸면 Boot 내장 처리가 먼저 가로챔
2. **검색 기록이 실제 화면 검색 경로에서 전혀 안 쌓임**: Task 4에서 `SummonerController.getByRiotId`(JSON API)에만 기록 로직을 넣었는데, 실제 웹사이트의 검색 흐름(메인 화면 검색 → `/summoners/{gameName}/{tagLine}` 페이지로 바로 이동)은 `PageController.profile()`이 `summonerService.findOrFetch()`를 **직접** 호출해서 그 API 컨트롤러를 아예 안 거침. Task 4의 라이브 검증은 API 엔드포인트를 직접 호출해서 확인했었기 때문에(정상 동작처럼 보였음) 이 갭을 못 잡았고, 이번 Task 5의 Playwright 검증(로그인 → 프로필 페이지 방문 → 마이페이지에서 검색 기록 확인)에서 마이페이지가 계속 비어있는 걸 보고 발견함. `PageController.profile()`에도 동일한 `searchHistoryService.record()` 호출 추가로 해결
3. **로그아웃 후 nav가 계속 로그인 상태로 보이는 것처럼 보였던 문제**: 실제로는 버그가 아니라 Playwright 테스트 스크립트의 캐시 재사용 문제였음(같은 URL `/`을 같은 브라우저 컨텍스트에서 두 번째 방문 시 디스크 캐시 재사용) — raw HTTP로 재확인한 결과 서버는 로그아웃 시 access_token/refresh_token/XSRF-TOKEN 쿠키 전부 `Max-Age=0`으로 정확히 지우고 있었음. 참고로만 기록: 실서비스에서도 이론상 동일한 캐시 재사용이 발생하면 로그아웃 직후 새로고침 없이 뒤로가기 등으로 nav가 잠깐 "로그인된 것처럼" 보일 수 있으나, `/mypage` 등 실제 보호된 동작은 서버 쪽에서 쿠키 기준으로 별도 검증되므로 화면 표시 지연일 뿐 실제 인가 우회는 아님 — README 트러블슈팅감으로 남겨둠

**완료 기준 — 확인됨(Playwright, 로컬)**: 회원가입 → 자동 로그인 → `/`로 리다이렉트, nav에 닉네임 표시(`/`, 프로필 페이지, `/mypage` 세 군데 모두 — `CurrentUserModelAdvice` 스코핑 확인) → 프로필 화면 즐겨찾기 버튼 클릭 시 "☆ 즐겨찾기" → "★ 즐겨찾기 해제" 토글 → 마이페이지에 방금 추가한 즐겨찾기 + 방문한 소환사 검색 기록 둘 다 표시 → 즐겨찾기 제거 버튼 클릭 시 목록에서 사라짐 → 로그아웃 시 nav가 로그인/회원가입 버튼으로 전환 → 로그아웃 후 `/mypage` 접근 시 `/login`으로 리다이렉트(비로그인 시 500 아님 확인) → 비로그인 상태로 `/mypage` 직접 접근해도 리다이렉트. 콘솔 에러 0건. 전체 스위트 116개 통과.

#### 6. 전 폼 Validation 통합 — 2~3h ✅ 완료 (Task 5보다 먼저 처리 — 로그인/가입 폼이 필드별 에러 메시지에 의존하므로 순서 변경)

- [x] 회원가입/로그인/즐겨찾기 DTO엔 `@Valid` + bean validation 애노테이션이 **Task 1~3에서 이미 붙어있었음**(`SignupRequest`: `@NotBlank`/`@Email`/`@Size`/`@AssertTrue`, `LoginRequest`: `@NotBlank`/`@Email`, `FavoriteRequest`: `@NotNull`) — 이번 Task에서 새로 붙일 게 없었음, 컨트롤러 3곳 모두 `@Valid` 이미 적용돼 있었음
- [x] 기존 `ApiExceptionHandler` 확장: `MethodArgumentNotValidException` 핸들러 추가. **실제로 한 일은 계획과 다름** — Boot의 `mvc.problemdetails.enabled=true`가 이미 이 예외를 400으로 자동 변환하고 있어서(Task 1에서 확인됨) 처음엔 "핸들러 불필요"로 판단했었는데, 막상 만들어서 라이브로 붙여보니 **핸들러 자체는 등록됐지만 호출이 안 됨**(기존 `ConstraintViolationException` 핸들러는 정상 작동해서 빈 로딩 자체는 확인됨) — Boot 내장 처리가 더 높은 우선순위로 먼저 가로챈 것으로 판단, `@Order(Ordered.HIGHEST_PRECEDENCE)`를 클래스에 추가해서 해결. 진짜 얻은 것은 필드별 메시지(`"email: 올바른 형식의 이메일 주소여야 합니다"` 등, Bean Validation 기본 메시지가 자동으로 한국어) — 기존엔 전부 제네릭 `"Invalid request content."`였음
- [x] 테스트: `ApiExceptionHandlerTest` 2개(단일 필드 에러, 복수 필드 에러 조인) — 순수 유닛 테스트라 Spring 디스패치 자체는 못 잡음(그래서 위 `@Order` 문제를 라이브 검증에서야 발견함), 라이브로 기존 회원가입/로그인/제약조건 위반 케이스 모두 재확인

**완료 기준 — 확인됨(라이브)**: 약관 미동의/닉네임 길이 위반 → `400` + `"nickname: 크기가 2에서 20 사이여야 합니다; agreedToTerms: 약관에 동의해야 합니다"`, 이메일 형식 오류 → `400` + `"email: 올바른 형식의 이메일 주소여야 합니다"`, 기존 `@Validated` 경로 파라미터 위반(`ConstraintViolationException`)도 그대로 정상, 정상 가입은 여전히 `201`. 유닛 테스트 2개 신규 + 전체 스위트 116개 통과.

#### 7. 테스트 커버리지 보강 — 1~2h

- [ ] Task 1~6에서 놓친 엣지 케이스 점검(만료된 refresh, 존재하지 않는 유저로 로그인 시도 등)
- [ ] `./gradlew test --rerun-tasks` 전체 통과 확인(캐시 아닌 실제 재실행)

#### 8. Swagger API 문서화 — 1~2h

- [ ] `springdoc-openapi` 의존성 추가 — 기존 Phase 1~3 API(소환사/매치/통계/티어이력) + 이 Phase의 Auth/즐겨찾기 API 전부 자동 스캔 적용
- [ ] **미결(§5)**: `/swagger-ui`를 prod에서도 열어둘지 — 학습 포트폴리오 성격이라 열어두는 쪽이 README 어필에 유리하지만, 공개 서버에 API 문서 노출은 표준 트레이드오프

### Track B (인프라 게이트)

#### 9. CI/CD 파이프라인 완성 — 2~3h (예산은 §10 "배포" 라인 7~9h에서 카운트, 진행상황만 이 문서에서 같이 추적)

- [ ] **선행 조건**: §0 블로커(인프라 조기 배포) 완료 — EC2 인스턴스 + Docker 세팅 없이는 아래 배포 스텝 자체가 무의미
- [ ] GitHub Actions: `push(main)` → 테스트 실행(실패 시 배포 중단) → Docker 이미지 빌드 → GHCR push → EC2 SSH 접속 → `docker compose pull && docker compose up -d`(§9.4)
- [ ] GitHub Secrets 등록: EC2 SSH 키, GHCR 토큰

#### 10. README 작성 — 2~3h (Track A 산출물 + Track B의 서비스 URL 둘 다 필요한 수렴 지점)

- [ ] ERD, 아키텍처 다이어그램
- [ ] 트러블슈팅 기록 — Phase 1~3에서 이미 쌓인 소재: league-v4 apex tier `rank="I"` 오분류 버그(TierScore), `MATCH_PARTICIPANTS` 교차수집으로 400+행 발생, claude-in-chrome이 로컬 개발 서버에 도달 못 해 Playwright로 전환한 건 등
- [ ] **서비스 URL** — Task 9(CI/CD) + §0 블로커 해소 후에만 채울 수 있음

**Phase 5 완료.**

---

## 4. Phase 5 완료 기준 (Definition of Done)

PROJECT_PLAN.md §4 Phase 5 체크리스트 전체 충족 + 아래 확인:

1. [ ] 회원가입 → 로그인(쿠키 발급 확인) → 인증 필요 API(즐겨찾기 등) 호출이 실제로 동작
2. [ ] 로그아웃 후 refresh 재시도가 거부됨(토큰 무효화 확인)
3. [ ] 마이페이지에서 즐겨찾기 추가/제거, 최근 검색 기록 조회가 화면에서 실제로 눈으로 확인 가능
4. [ ] 잘못된 입력(형식 오류 이메일 등)에 대해 폼/`@Valid` 둘 다 일관된 에러 응답
5. [ ] Swagger UI에서 Phase 1~5 API 전체가 조회 가능
6. [ ] (Track B) CI/CD 파이프라인으로 push 후 자동 배포 확인, README에 실서비스 URL 기재

### 실측 트래킹

| 항목 | 추정 | 완료일 | 메모 |
|---|---|---|---|
| 1. USERS + 회원가입 | 2~3h | 2026-08-08 | |
| 2. JWT 로그인/로그아웃/재발급 | 5~6h | 2026-08-08 | CSRF 토큰 구현은 Task 3로 이동(위 §5 참고), 그만큼 Task 2 실작업은 예상보다 가벼웠고 Task 3가 무거워짐 |
| 3. 즐겨찾기 API | 1.5~2h | 2026-08-08 | CSRF 토큰 구현이 Task 2에서 이관돼 실작업은 3~4h로 예상보다 늘어남 |
| 4. 검색 기록 저장/조회 | 1.5~2h | 2026-08-08 | 예상대로 가벼웠음 — Task 2의 JwtAuthenticationFilter가 이미 선택적 인증을 공짜로 제공 |
| 5. 화면(로그인/회원가입/마이페이지) | 3~4h | 2026-08-10 | 검색 기록이 실제 화면 경로에서 안 쌓이던 버그(Task 4 갭)를 여기서 발견·수정 |
| 6. Validation 통합 | 2~3h | 2026-08-10 | Task 5보다 먼저 진행. DTO 애노테이션은 이미 있어서 실작업은 `@Order` 우선순위 이슈 하나 잡는 게 대부분 |
| 7. 테스트 커버리지 보강 | 1~2h | | |
| 8. Swagger | 1~2h | | |
| **Track A 소계** | **18~24h** | | |
| 9. CI/CD (배포 라인 별도 카운트) | 2~3h | | §0 블로커 해소 후 착수 |
| 10. README | 2~3h | | Track A+B 수렴 후 마무리 |

---

## 5. 결정 사항 & 미결 사항

### 확정 (PROJECT_PLAN.md에 이미 명시됨 — 재확인용, 이번 세션에서 새로 정한 것 아님)

| 항목 | 결정 | 근거 |
|---|---|---|
| 토큰 저장 위치 | access/refresh 둘 다 httpOnly + Secure 쿠키(Secure는 dev=false/prod=true로 프로필 분리) | §6 — SSR 페이지 이동엔 Authorization 헤더 못 붙임, localStorage는 XSS 취약 |
| CSRF 방어 | SameSite=Lax(Task 2) + `/api/users/me/**` 대상 이중 제출 쿠키 필터(Task 3, `CsrfDoubleSubmitFilter` — Spring Security 기본 `CsrfFilter` 대신 직접 구현) | §6 / Task 3 완료 기준 |
| Refresh Token 저장 | DB에 SHA-256 해시로 저장(원문 금지), `revoked` 플래그로 무효화 | §6 |
| 아이디 찾기 | 미제공 — 이메일이 로그인 ID라 이메일 주소 enumeration 방지 | §6 |
| 비밀번호 해싱 | bcrypt | §3/§6 |
| 이메일 인증/재설정/로그인 잠금 | 기본 이월 | §4/§10 |
| Access/Refresh TTL | access 30분, refresh 14일 | Task 2에서 확정(§6 범위 안에서 선택) |
| Refresh 토큰 회전 | 미회전(만료까지 재사용) | Task 2에서 확정 |

### 미결 (grilling 대상 — 착수 전 확정 또는 기본값 수용 필요)

| 항목 | 열린 질문 | 기본값(확정 아님) |
|---|---|---|
| Swagger prod 노출 여부 | 포트폴리오 어필 vs 공개 API 문서 노출 트레이드오프(Task 8 참고) | dev 전용, prod 비노출 |
