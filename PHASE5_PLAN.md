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
Task 5: 로그인/회원가입/마이페이지 화면
            │
            ▼
Task 6: 전 폼 Validation 통합 (기존 ApiExceptionHandler 확장)
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

#### 2. JWT 로그인/로그아웃/재발급 — 5~6h

- [ ] `REFRESH_TOKENS` 엔티티(§6) — `token_hash`로 저장(원문 저장 금지), `revoked` 플래그로 무효화
- [ ] `POST /api/auth/login` — 이메일/비번 검증 후 access(15~30분)/refresh(7~14일) 토큰을 httpOnly+Secure 쿠키로 응답(§6 확정 — Thymeleaf 서버 렌더 페이지 이동엔 Authorization 헤더를 못 붙이고, localStorage 저장은 XSS에 취약). 5회 연속 실패 잠금은 이월(Task 1 참고)이라 미구현
- [ ] `POST /api/auth/logout` — refresh token revoke + 쿠키 삭제
- [ ] `POST /api/auth/refresh` — refresh 쿠키 검증(해시 대조) 후 access 재발급
- [ ] Spring Security 필터 — 쿠키의 JWT를 파싱해 인증 컨텍스트 구성. CSRF는 SameSite=Lax + 상태 변경 API의 CSRF 토큰으로 방어(§6 확정)
- [ ] 테스트: 로그인 성공/실패(잘못된 비번), access 재발급, 로그아웃 후 refresh 거부, revoked 토큰 거부

#### 3. 즐겨찾기 API — 1.5~2h

- [ ] `FAVORITES` 엔티티(§6)
- [ ] `GET/POST /api/users/me/favorites`, `DELETE /api/users/me/favorites/{summonerId}` — 전부 JWT 필요
- [ ] 테스트: 추가/삭제/중복 추가 방지/목록 조회

#### 4. 최근 검색 기록 저장/조회 — 1.5~2h

- [ ] `SEARCH_HISTORY` 엔티티(§6)
- [ ] `GET /api/users/me/search-history` — JWT 필요
- [ ] **미결(§5)**: 기록 저장 지점 — `/api/summoners/riot-id/{gameName}/{tagLine}`는 현재 인증 불필요 공개 엔드포인트(SEARCH_COUNTS만 증가, §7). 로그인 사용자의 검색을 기록하려면 이 엔드포인트에 "선택적 인증"(쿠키 있으면 파싱해서 기록, 없어도 통과)을 추가해야 함 — 신규 분기점, 착수 전 확정 필요
- [ ] 테스트: 로그인 사용자 검색 시 기록 저장, 비로그인은 미저장

#### 5. 로그인/회원가입/마이페이지 화면 — 3~4h

- [ ] `login.html`/`signup.html` — Bootstrap 폼 컴포넌트 + vanilla JS + fetch(§3 화면 기술 선택 그대로)
- [ ] `mypage.html` — 즐겨찾기 목록(제거 버튼) + 최근 검색 기록
- [ ] nav에 로그인 상태 표시(로그인/로그아웃 버튼 전환) — 쿠키 기반이라 SSR 시점에 판별 가능
- [ ] 프로필 화면에 즐겨찾기 토글 버튼 추가(로그인 시에만 노출)
- [ ] 화면 검증은 Phase 3 Task 4 관행대로 브라우저(Playwright)로 직접 확인 — 신규 API는 Task 1~4에서 이미 유닛 테스트로 커버

#### 6. 전 폼 Validation 통합 — 2~3h

- [ ] 회원가입/로그인 DTO에 `@Valid` + bean validation 애노테이션(`@Email`, `@NotBlank`, `@Size` 등)
- [ ] 기존 `ApiExceptionHandler`(`@RestControllerAdvice`, `src/main/java/com/lolstats/controller/ApiExceptionHandler.java` — 이미 있고 `ConstraintViolationException`만 처리 중, 클래스 주석에 "Future Phases add handlers here"라고 이미 예고돼 있음) 확장: `MethodArgumentNotValidException`(`@RequestBody` + `@Valid` 실패) 핸들러 추가. **주의**: `application.yml`의 `mvc.problemdetails.enabled: true`가 이미 `ResponseStatusException`(`MatchController`/`SummonerController`의 404 등)을 ProblemDetail로 정규화하고 있으므로, 새 핸들러가 기존 에러 응답 모양을 바꾸지 않는지 확인
- [ ] 테스트: 잘못된 이메일 형식/빈 비밀번호 등 400 + ProblemDetail 형태 확인

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
| 2. JWT 로그인/로그아웃/재발급 | 5~6h | | |
| 3. 즐겨찾기 API | 1.5~2h | | |
| 4. 검색 기록 저장/조회 | 1.5~2h | | |
| 5. 화면(로그인/회원가입/마이페이지) | 3~4h | | |
| 6. Validation 통합 | 2~3h | | |
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
| 토큰 저장 위치 | access/refresh 둘 다 httpOnly + Secure 쿠키 | §6 — SSR 페이지 이동엔 Authorization 헤더 못 붙임, localStorage는 XSS 취약 |
| CSRF 방어 | SameSite=Lax + 상태 변경 API의 CSRF 토큰 | §6 |
| Refresh Token 저장 | DB에 해시로 저장(원문 금지), `revoked` 플래그로 무효화 | §6 |
| 아이디 찾기 | 미제공 — 이메일이 로그인 ID라 이메일 주소 enumeration 방지 | §6 |
| 비밀번호 해싱 | bcrypt | §3/§6 |
| 이메일 인증/재설정/로그인 잠금 | 기본 이월 | §4/§10 |

### 미결 (grilling 대상 — 착수 전 확정 또는 기본값 수용 필요)

| 항목 | 열린 질문 | 기본값(확정 아님) |
|---|---|---|
| Access/Refresh 정확한 TTL | §6은 범위만 제시(access 15~30분, refresh 7~14일) | access 30분, refresh 14일(느슨하게 시작, 필요시 축소) |
| Refresh 토큰 회전(rotation) 여부 | 매 refresh 호출마다 새 refresh 토큰을 발급할지, 만료까지 같은 토큰을 재사용할지 — 미회전이면 탈취된 refresh가 만료 시점까지 계속 유효 | 미회전(단순 구현 우선, 트레이드오프는 README 트러블슈팅에 기록) |
| SEARCH_HISTORY 기록 지점 | 공개 검색 엔드포인트(`/api/summoners/riot-id/...`)에 "선택적 인증" 추가 필요(Task 4 참고) | 선택적 인증 추가 |
| Swagger prod 노출 여부 | 포트폴리오 어필 vs 공개 API 문서 노출 트레이드오프(Task 8 참고) | dev 전용, prod 비노출 |
