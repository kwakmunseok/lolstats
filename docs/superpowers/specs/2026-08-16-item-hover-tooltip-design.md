# 아이템 호버 툴팁 — 설계

> 사용자 피드백(2026-08-16): "아이템에 마우스 오버 하면 이게 무슨 아이템인지, 스펙이 어떤지가 나오면 좋겠음."

## 배경

현재 아이템은 `profile.html`(최근 매치 카드)과 `match-detail.html`(팀별 테이블)에서 아이콘 이미지로만 표시된다. 이름이나 효과 설명이 전혀 없어 아이콘만 보고 바로 알아보지 못하는 사용자는 어떤 아이템인지 확인할 방법이 없다.

## 범위

- 적용 화면: `profile.html`(최근 매치 카드 목록), `match-detail.html`(팀 1/팀 2 테이블) — 둘 다 아이템 아이콘이 나오는 유일한 두 화면
- 비주얼 스타일링(Riot 자체 태그 강조 등)은 이번 범위에서 제외 — 사용자가 실제 결과를 보고 별도 피드백

## 내용: 무엇을 보여줄 것인가

Riot Data Dragon의 `item.json`엔 아이템마다 게임 내 툴팁과 동일한 공식 `description`(스탯 + 고유 효과 HTML)이 이미 있다. 이걸 그대로 노출한다 — 직접 스탯 포맷팅 로직을 새로 만들지 않고 Riot가 이미 제공하는 텍스트를 그대로 사용.

- 포함: 아이템 이름, 스탯(공격력/체력 등), 고유 지속효과·사용효과 설명
- 제외: 골드 비용, 빌드 트리(합성 경로) — 사용자가 요청한 범위(이름+스펙) 밖

## 구현 방식

**Bootstrap Popover** 사용 (프로젝트가 이미 Bootstrap 5.3.3 CSS를 로드 중 — 새 프론트엔드 의존성 추가 없음).

현재 `layout.html`엔 Bootstrap **CSS만** 로드되고 JS는 없음 — Popover 동작에 필요한 `bootstrap.bundle.min.js`(Popper 포함) CDN 스크립트 태그를 `<head>`에 추가.

### 데이터 흐름 변경

1. **`ItemListResponse`** (`client/ddragon/dto/ItemListResponse.java`): `ItemData` 레코드에 `description` 필드 추가 (Data Dragon 응답의 `description` 그대로)
2. **`DataDragonService.ItemInfo`**: `record ItemInfo(int id, String name, String imageUrl, String description)` — 필드 하나 추가, `refresh()`의 매핑 로직에 반영
3. **`PageController`**: `toParticipantView()`가 아이템 이미지 URL 리스트를 만들 때 description도 함께 담아야 함 — 현재 `List<String> itemImageUrls`는 URL만 담는 단순 리스트라, `(imageUrl, description)` 쌍을 담을 작은 레코드(`ItemView(String imageUrl, String description)`)로 교체
   - `ParticipantView.itemImageUrls: List<String>` → `ParticipantView.items: List<ItemView>`
4. **템플릿** (`profile.html`, `match-detail.html`): 아이템 `<img>`에 `data-bs-toggle="popover"` + `data-bs-content`(description), 아이템 이름을 `data-bs-title`로. 페이지 로드 시 `[data-bs-toggle="popover"]` 전체에 대해 Bootstrap Popover 초기화하는 인라인 스크립트 한 줄(`html: true` 옵션 필요 — description이 HTML이므로)

### 에러 처리

- 빈 아이템 슬롯(기존에도 `th:if="${item}"`으로 스킵 중) — 그대로 유지, description 없음
- `description`이 비어있거나 null인 아이템(드묾) — popover 속성 자체를 안 붙여서 호버해도 아무 반응 없음(에러 아님, 조용히 스킵)
- Data Dragon description에 Riot 자체 태그(`<mainText>`, `<attention>` 등)가 섞여 있음 — 브라우저가 알 수 없는 태그를 무시하고 텍스트만 렌더링하므로 기능적으로는 문제없음(볼드/색상 강조가 안 먹을 뿐)

## 테스트

- **`DataDragonServiceTest`**: 기존 `itemLookup_worksByRawItemId`에 `description` 값 검증 추가, 테스트 픽스처(`stubDdragonResponses`)의 `ItemData`에 description 채우기
- **화면 검증**: 로컬 Playwright로 두 화면 모두에서 아이템 호버 시 팝오버가 뜨는지, 이름/설명 텍스트가 보이는지 확인 (이 프로젝트의 기존 관행 — Phase 3/5에서 화면 단위 검증은 항상 Playwright로 진행)

## 이번 범위 밖 (명시적 제외)

- Riot 커스텀 태그에 대한 CSS 스타일링(볼드/색상 강조) — 사용자가 실제 결과 확인 후 별도 요청 시 진행
- 골드 비용/빌드 트리 표시
