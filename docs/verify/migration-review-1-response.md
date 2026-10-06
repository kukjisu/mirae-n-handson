# 이관 검증 리뷰 1 — 응답

대상 리뷰: `docs/verify/migration-review-1.md`
이관 범위: 커밋 `cab640f` (`legacy/item-bank-php/search.php` → `GET /api/items/search`)
코드 수정: 없음

## 이슈 분류

| # | 심각도 | 분류 | 이유 |
|---|---|---|---|
| 1 | 경고 | 확인 필요 → **결정: 범위 내 (a)** | `docs/assignment/*` 는 이관 커밋 `cab640f` 가 아니라 1회차 커밋 `22d580b` 에서 추가됨. 리뷰가 diff 기준을 `upstream/main`(`1bb4f2f`)으로 잡아 섞였다. |
| 2 | 경고 | 확인 필요 → **결정: 범위 내 (a)** | `CLAUDE.md` · `.claude/skills/*` 도 `22d580b` 에서 추가됨. #1 과 같은 이유. |
| 3 | 제안 | 수용 (수정 없음) | 이관 코드가 `BR-xx` 를 근거로 참조함(`modern/api/src/main/java/com/example/item/ItemSearchCondition.java:15`). PR 포함 여부는 사람이 결정한다. |
| 4 | 제안 | 수용 (수정 없음) | `ItemSearchService.java:59-61` 에서 `byId::get` 이 null 이면 NPE. 두 조회가 같은 읽기 전용 트랜잭션(`ItemSearchService.java:18`) 안이라 가능성은 낮음. |

수용한 이슈 중 치명 · 경고는 0건이라 코드는 수정하지 않았다.

## 결정 (사람)

- #1 · #2 는 **(a)** 로 한다. 1회차 문서 · Skill 커밋(`22d580b` 와 그 이전)은 교안 설계상 같은 브랜치에 두는 산출물이다. 이관 범위는 `cab640f` 다. 브랜치는 쪼개지 않는다.
- 따라서 리뷰의 "요청 범위 이탈 없음" 기준은 `cab640f` 기준으로 충족한다.

## 남겨 둔 제안 (수정하지 않음)

- #3: `docs/item-bank/*` 를 이관 PR 에 포함할지 결정
- #4: `ItemSearchService.loadInOrder` 에서 null 행을 건너뛸지, 의도적으로 실패시킬지 명시

## 레거시 의심 동작 (이관 이슈 아님, 고치지 않음)

- 난이도가 비어 있으면 `level < 5` 라 난이도 5 문항이 빠짐. 주석은 "1~5 모두 포함"(`legacy/item-bank-php/search.php:213-215`). 이관 코드도 똑같이 동작한다(`ItemSearchRepository.java:28`).
- LIKE 에서 `%` · `_` 를 이스케이프하지 않음(`legacy/item-bank-php/search.php:98`). 이관 코드도 똑같이 동작한다(`ItemSearchCondition.java:74-77`).

## 리뷰의 "확인 필요" 해소

| 항목 | 결과 |
|---|---|
| `./gradlew test` 가 캐시였을 가능성 | `cd modern/api && ./gradlew test --rerun-tasks` → 46 통과 · 0 실패 · 0 skip |
| 8080 이 HEAD 코드인지 | 사람이 HEAD 로 `bootRun` 재기동 후 `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` → 30 통과 · 0 실패 · 1 skip (`tests/example-units.test.js:21`, `TARGET_BASE_URL` 설정 시 `skipIf` 로 건너뛰도록 설계된 레거시 전용 케이스) |
| diff 기준 브랜치 | 위 결정 (a) 로 해소 |
