# Riot Personal API Key 신청 내용 정리

**신청 경로**: developer.riotgames.com 로그인 → 대시보드 [Register Product] 또는 `/app-type` → **PERSONAL API KEY** 쪽 [REGISTER PRODUCT] → 정책 동의 모달 [I AGREE] (계정당 최초 1회일 수 있음, 다시 뜨면 동의) → 신청 폼(`/create-app`)

## 폼 입력값

| 필드 | 입력값 |
|---|---|
| Product Name* | `LoL Stats` (계획서 가칭 — 확정 이름 있으면 그걸로) |
| Product Description* | 아래 초안 붙여넣기 (**GitHub URL 한 줄 추가**) |
| Product Group* | `Default Group` (기본값 유지) |
| Product URL | https://github.com/kwakmunseok/lolstats |
| Product Game Focus* | `League of Legends` |

## Product Description (복사용)

> 마지막에 GitHub URL만 본인 것으로 바꿔서 사용

```
A personal learning project: a League of Legends match history and statistics
website for the KR region, built with Java/Spring Boot. Users search a Riot ID
to view summoner profile, ranked tier, recent match history, and per-champion
statistics aggregated from matches stored in our own database.

APIs used: account-v1 (Riot ID to PUUID resolution), summoner-v4 (profile),
league-v4 (ranked entries by PUUID), match-v5 (match list and details).
Static assets (champion/item/rune icons) come from Data Dragon.

To respect rate limits, all fetched matches are cached permanently in our
database and never re-requested, and all API calls pass through an
application-level rate limiter (20 req/s, 100 req/2min) with a background
collection queue.

This is a non-commercial project for my own use and backend development
practice. It will not be promoted publicly.

Source code: https://github.com/kwakmunseok/lolstats
```

## 신청 시 참고

- 스크린샷·동작 화면 증빙 필드 **없음** — Description 텍스트가 심사 대상. "불완전한 설명은 반려(rejected)"라고 폼에 명시되어 있으므로 사용 API 목록을 반드시 포함할 것 (위 초안에 포함됨)
- 승인 전까지는 지금처럼 Dev Key로 개발 (24h마다 갱신)
- 승인되면 포털 대시보드의 해당 앱(APPS) 페이지에서 키 확인
- **정책 유의**: Personal Key로 "public consumption"(오픈 알파/베타 포함) 운영 금지. 배포 URL은 학습·본인 확인용으로만 쓰고 불특정 다수 대상 홍보 금지 — 공개 서비스 전환 시 Production Key 필요 (계획서 §11 각주)
- GitHub repo는 **API 키 커밋 금지** 상태로 올릴 것 (`.env` / application-*.yml 시크릿 분리 — 계획서 §9.5). 키가 커밋된 repo를 URL로 제출하면 그 자체가 정책 위반("properly secure your API key")
  - ✅ 2026-07-29: repo public 게시 완료, `.env`/`.env.*` gitignore 처리 + 전체 히스토리 시크릿 스캔 완료 (커밋된 키 없음)
