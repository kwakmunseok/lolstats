# 아이템 호버 툴팁 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 매치 카드/매치 상세 화면의 아이템 아이콘에 마우스를 올리면 Riot 공식 설명(이름+스탯+고유 효과)이 팝오버로 뜨게 한다.

**Architecture:** Data Dragon `item.json`의 `description` 필드를 백엔드에서 그대로 캐싱·전달(`DataDragonService` → `PageController`)하고, 프론트엔드는 이미 로드 중인 Bootstrap 5 Popover(`data-bs-toggle="popover"`)로 렌더링한다. 새 라이브러리 없음, 새 API 엔드포인트 없음 — 기존 SSR 모델 조립 경로에 필드 하나가 늘어나는 구조.

**Tech Stack:** Spring MVC(Thymeleaf SSR), Bootstrap 5.3.3(Popover, CDN, 이미 로드 중), 기존 `DataDragonService` 캐싱 계층.

## Global Constraints

- 비주얼 스타일링(Riot 커스텀 태그 강조 등)은 이번 범위 밖 — 텍스트만 나오면 됨 (2026-08-16 스펙 승인 조건)
- 적용 화면은 정확히 두 곳: `profile.html`, `match-detail.html` — 그 외 화면 변경 없음
- 새 프론트엔드 의존성 추가 금지 — Bootstrap은 이미 두 화면 다 로드 중이거나(profile.html) 이번 작업에서 기존 패턴대로 페이지 단위로 추가(match-detail.html)

---

### Task 1: Data Dragon 아이템 조회에 `description` 추가

**Files:**
- Modify: `src/main/java/com/lolstats/client/ddragon/dto/ItemListResponse.java`
- Modify: `src/main/java/com/lolstats/service/DataDragonService.java:110-114` (매핑), `:140` (레코드)
- Test: `src/test/java/com/lolstats/service/DataDragonServiceTest.java:43-44` (픽스처), `:64-71` (테스트)

**Interfaces:**
- Produces: `DataDragonService.ItemInfo`에 `description()` 접근자 추가 — Task 2가 `dataDragonService.getItem(id).map(item -> item.description())`로 소비함

- [ ] **Step 1: 실패하는 테스트부터 작성**

`src/test/java/com/lolstats/service/DataDragonServiceTest.java`의 `stubDdragonResponses` 메서드에서 아이템 픽스처를 다음으로 교체:

```java
when(client.getItems(version)).thenReturn(new ItemListResponse(Map.of(
        "1001", new ItemListResponse.ItemData("장화", "<mainText>이동속도 +25</mainText>",
                new ItemListResponse.ItemImage("1001.png")))));
```

`itemLookup_worksByRawItemId` 테스트에 아래 assertion 추가:

```java
@Test
void itemLookup_worksByRawItemId() {
    Optional<DataDragonService.ItemInfo> boots = service.getItem(1001);

    assertTrue(boots.isPresent());
    assertEquals("장화", boots.get().name());
    assertEquals("https://ddragon.leagueoflegends.com/cdn/16.14.1/img/item/1001.png", boots.get().imageUrl());
    assertEquals("<mainText>이동속도 +25</mainText>", boots.get().description());
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.lolstats.service.DataDragonServiceTest" --no-daemon`

Expected: 컴파일 실패 — `ItemListResponse.ItemData(String, String, ItemImage)` 생성자가 없고(현재는 2-arg), `ItemInfo`에 `description()` 메서드가 없음

- [ ] **Step 3: 최소 구현**

`src/main/java/com/lolstats/client/ddragon/dto/ItemListResponse.java` 전체를 다음으로 교체:

```java
package com.lolstats.client.ddragon.dto;

import java.util.Map;

// item.json: data is keyed directly by itemId (as a string) - no separate "key" field needed.
public record ItemListResponse(Map<String, ItemData> data) {

    public record ItemData(String name, String description, ItemImage image) {
    }

    public record ItemImage(String full) {
    }
}
```

`src/main/java/com/lolstats/service/DataDragonService.java:110-114`을 다음으로 교체:

```java
        this.itemsById = items.data().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> Integer.parseInt(e.getKey()),
                        e -> new ItemInfo(Integer.parseInt(e.getKey()), e.getValue().name(),
                                CDN_BASE + version + "/img/item/" + e.getValue().image().full(),
                                e.getValue().description())));
```

`src/main/java/com/lolstats/service/DataDragonService.java:140`을 다음으로 교체:

```java
    public record ItemInfo(int id, String name, String imageUrl, String description) {
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.lolstats.service.DataDragonServiceTest" --no-daemon`

Expected: PASS (6개 테스트 전부)

- [ ] **Step 5: 전체 스위트 회귀 확인**

Run: `./gradlew test --rerun-tasks --no-daemon`

Expected: BUILD SUCCESSFUL — `ItemInfo`/`ItemData` 생성 지점은 이 두 파일이 전부였음(사전 확인 완료, `grep -rn "ItemData(\|ItemInfo("` 결과 이 두 곳뿐)이라 다른 곳이 깨질 이유 없음

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/lolstats/client/ddragon/dto/ItemListResponse.java src/main/java/com/lolstats/service/DataDragonService.java src/test/java/com/lolstats/service/DataDragonServiceTest.java
git commit -m "feat: Cache item description from Data Dragon"
```

---

### Task 2: 매치 카드/상세 화면에 아이템 팝오버 연결

**Files:**
- Modify: `src/main/java/com/lolstats/controller/PageController.java:178-200`(`toParticipantView`), `:242-247`(`ParticipantView` 레코드)
- Modify: `src/main/resources/templates/profile.html:86-91`, `:132-221`(스크립트 블록에 초기화 한 줄 추가)
- Modify: `src/main/resources/templates/match-detail.html:34-38`(팀1), `:65-69`(팀2), `:74` 근처(스크립트 태그 추가)

**Interfaces:**
- Consumes: Task 1의 `DataDragonService.ItemInfo.imageUrl()`/`.description()`
- Produces: `PageController.ItemView(String imageUrl, String description)` — 두 템플릿의 `th:each` 루프가 이 타입의 리스트를 순회함. `ParticipantView.items()`가 기존 `itemImageUrls()`를 대체(이름과 타입 둘 다 변경 — 아래 3곳 전부 갱신 필요)

- [ ] **Step 1: `PageController`에 `ItemView` 레코드 추가 + `ParticipantView` 필드 교체**

`src/main/java/com/lolstats/controller/PageController.java:242-247`(현재 `ParticipantView` 레코드)을 다음으로 교체:

```java
    public record ParticipantView(
            String gameName, String tagLine, String championName, String championImageUrl, String teamPosition,
            Integer kills, Integer deaths, Integer assists, Boolean win,
            String spell1ImageUrl, String spell2ImageUrl, List<ItemView> items,
            String keystoneIconUrl, String secondaryStyleIconUrl) {
    }

    public record ItemView(String imageUrl, String description) {
    }
```

- [ ] **Step 2: `toParticipantView()`가 `ItemView` 리스트를 만들도록 수정**

`src/main/java/com/lolstats/controller/PageController.java:178-200`(현재 `toParticipantView` 메서드 전체)을 다음으로 교체:

```java
    private ParticipantView toParticipantView(MatchParticipant p) {
        String championName = dataDragonService.getChampion(p.getChampionId())
                .map(DataDragonService.ChampionInfo::name).orElse("?");
        String championImageUrl = dataDragonService.getChampion(p.getChampionId())
                .map(DataDragonService.ChampionInfo::imageUrl).orElse(null);

        List<ItemView> items = List.of(objectMapper.readValue(p.getItemsJson(), Integer[].class)).stream()
                .map(id -> id == 0 ? null : dataDragonService.getItem(id)
                        .map(item -> new ItemView(item.imageUrl(), item.description()))
                        .orElse(null))
                .toList();

        JsonNode perks = objectMapper.readTree(p.getRunesJson());
        String keystoneIconUrl = dataDragonService.getRuneIconUrl(
                perks.path("styles").path(0).path("selections").path(0).path("perk").asInt(0)).orElse(null);
        String secondaryStyleIconUrl = dataDragonService.getRuneIconUrl(
                perks.path("styles").path(1).path("style").asInt(0)).orElse(null);

        return new ParticipantView(
                p.getGameName(), p.getTagLine(), championName, championImageUrl, p.getTeamPosition(),
                p.getKills(), p.getDeaths(), p.getAssists(), p.getWin(),
                dataDragonService.getSpell(p.getSpell1Id()).map(DataDragonService.SpellInfo::imageUrl).orElse(null),
                dataDragonService.getSpell(p.getSpell2Id()).map(DataDragonService.SpellInfo::imageUrl).orElse(null),
                items, keystoneIconUrl, secondaryStyleIconUrl);
    }
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava --no-daemon`

Expected: BUILD SUCCESSFUL (템플릿은 런타임에 평가되므로 아직 안 고쳐도 컴파일 자체는 통과함 — 이 스텝은 Java 쪽 타입 변경이 온전한지만 확인)

- [ ] **Step 4: `profile.html` 아이템 루프에 팝오버 속성 연결**

`src/main/resources/templates/profile.html:86-91`을 다음으로 교체:

```html
                    <div class="d-flex gap-1">
                        <div th:each="item : ${m.player.items}"
                             class="bg-body-secondary rounded" style="width: 28px; height: 28px;">
                            <img th:if="${item}" th:src="${item.imageUrl}" width="28" height="28" class="rounded"
                                 alt="아이템" data-bs-toggle="popover" data-bs-html="true" data-bs-trigger="hover focus"
                                 th:attr="data-bs-content=${item.description}">
                        </div>
                    </div>
```

- [ ] **Step 5: `profile.html`에 팝오버 초기화 스크립트 추가**

`src/main/resources/templates/profile.html:132` 바로 다음 줄(`/*<![CDATA[*/` 다음 줄, 기존 `const gameName = ...` 앞)에 아래 한 줄 추가:

```javascript
    document.querySelectorAll('[data-bs-toggle="popover"]').forEach(el => new bootstrap.Popover(el));
```

(이 페이지는 이미 `bootstrap.bundle.min.js`를 로드 중이므로 — line 130, nav-tabs용 — 스크립트 태그 추가 불필요, 초기화 호출만 추가)

- [ ] **Step 6: `match-detail.html` 팀1/팀2 아이템 루프에 팝오버 속성 연결**

`src/main/resources/templates/match-detail.html:34-38`(팀1)을 다음으로 교체:

```html
                <td>
                    <span th:each="item : ${p.items}">
                        <img th:if="${item}" th:src="${item.imageUrl}" width="24" height="24" class="rounded"
                             alt="아이템" data-bs-toggle="popover" data-bs-html="true" data-bs-trigger="hover focus"
                             th:attr="data-bs-content=${item.description}">
                    </span>
                </td>
```

`src/main/resources/templates/match-detail.html:65-69`(팀2)를 동일하게 교체:

```html
                <td>
                    <span th:each="item : ${p.items}">
                        <img th:if="${item}" th:src="${item.imageUrl}" width="24" height="24" class="rounded"
                             alt="아이템" data-bs-toggle="popover" data-bs-html="true" data-bs-trigger="hover focus"
                             th:attr="data-bs-content=${item.description}">
                    </span>
                </td>
```

- [ ] **Step 7: `match-detail.html`에 Bootstrap JS + 팝오버 초기화 스크립트 추가**

`src/main/resources/templates/match-detail.html:74`(`</main>` 다음 줄, `</body>` 앞)에 추가:

```html
<!-- 이 화면에서만 쓰여서 공용 layout.html이 아니라 여기서 직접 로드(profile.html과 동일 패턴 -
     data-bs-toggle="popover" 아이템 툴팁에 필요) -->
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
<script>
    document.querySelectorAll('[data-bs-toggle="popover"]').forEach(el => new bootstrap.Popover(el));
</script>
```

- [ ] **Step 8: 로컬에서 전체 스위트 재확인**

Run: `./gradlew test --rerun-tasks --no-daemon`

Expected: BUILD SUCCESSFUL — PageController는 전용 단위 테스트가 없는 상태(이 프로젝트 기존 관행 — 화면 단위 동작은 Playwright로 검증), 여기서는 다른 서비스/컨트롤러 테스트가 이 변경으로 안 깨지는지만 확인

- [ ] **Step 9: Playwright로 실제 호버 동작 확인**

로컬 dev 서버(`./gradlew bootRun`, MySQL/Redis 컨테이너 기동 상태)에서 이미 매치 기록이 있는 소환사 프로필 페이지와 매치 상세 페이지 각각에서, 아이템 아이콘에 호버 후 팝오버가 뜨는지, 텍스트가 보이는지 스크린샷으로 확인. 새 스크립트를 짤 필요 없이 이 세션에서 이미 여러 번 쓴 것과 같은 방식(`mcp__claude-in-chrome__computer` hover 액션 + screenshot)으로 충분.

Expected: 두 화면 모두 아이템 아이콘에 마우스를 올리면 이름+스탯+효과 텍스트가 담긴 팝오버가 나타남. 빈 아이템 슬롯(아이콘 없음)엔 아무 반응 없음.

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/lolstats/controller/PageController.java src/main/resources/templates/profile.html src/main/resources/templates/match-detail.html
git commit -m "feat: Show item name/stats/effects on hover (Bootstrap Popover)"
```

- [ ] **Step 11: 배포 후 실사이트에서 최종 확인**

`git push origin master` → CI/CD 파이프라인(test → build → self-hosted 배포) 통과 확인 → `https://3-34-80-155.sslip.io`에서 실제 소환사 프로필/매치 상세 페이지 열어서 아이템 호버 재확인.

---

## Self-Review 완료 사항

- **스펙 커버리지**: 적용 범위(두 화면) → Task 2 Step 4/6, 내용(Riot description 그대로) → Task 1, 구현 방식(Bootstrap Popover, 새 의존성 없음) → Task 2 Step 5/7(기존 로드 재사용 또는 기존 패턴대로 페이지 단위 추가), 에러 처리(빈 슬롯/description 없음) → 기존 `th:if="${item}"` 그대로 유지 + description 없으면 그냥 빈 팝오버(치명적이지 않음, 별도 분기 불필요). 전부 커버됨.
- **플레이스홀더 스캔**: 없음 — 모든 스텝에 실제 코드/명령어 포함.
- **타입 일관성**: `ItemView(imageUrl, description)` → Task 1의 `ItemInfo.imageUrl()`/`.description()`과 필드명 일치, Task 2 전체에서 `items`/`ItemView` 명칭 통일(과거 `itemImageUrls`는 어디에도 안 남음 — profile.html, match-detail.html 양쪽 다 갱신).
