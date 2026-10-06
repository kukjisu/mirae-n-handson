# 이관 검증 리뷰 2 — `upstream/main...HEAD`

요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관. (코드는 수정하지 않고 보고만 한다.)
대상: `legacy/item-bank-php/search.php` → `GET /api/items/search` (HEAD `3593687`)
리뷰 1(`migration-review-1.md`) 이후 변경: 커밋 `b7f79f8`, `3593687` — `modern/` 변경 없음(`git diff b7f79f8 HEAD -- modern` 비어 있음).

## 판정: 승인

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `cd modern/api && ./gradlew test --rerun-tasks` → 46 통과 · 0 실패 · 0 skip · 0 오류(결과 XML 집계, 캐시 아님). `cd characterization && npm test`(레거시 8081) → 31 통과 · 0 실패. `TARGET_BASE_URL=http://localhost:8080 npm test`(새 API) → 30 통과 · 0 실패 · 1 skip(`tests/example-units.test.js:21`, `it.skipIf(legacyOnly)` 기존 설계). 실패한 테스트 없음. 새 테스트의 `@Disabled` · `skip` · `.only` 없음. |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. 비밀값 리터럴 없음, SQL 문자열 결합 없음(`ItemSearchRepository.java:22-40` 은 JPQL 파라미터 바인딩), 삭제 · 덮어쓰기 코드 없음. |
| 요청 범위 이탈 없음 | 충족 (사람 결정에 의존) | 요청 vs 변경: `modern/api` 10개 파일(`ItemSearch*` 소스 5 + 테스트 4 + …)과 `characterization/tests/item-bank.test.js` · 스냅샷은 이관으로 설명됨. 그 밖 파일(`CLAUDE.md`, `.claude/skills/*`, `docs/assignment/*`, `docs/item-bank/*`, `docs/verify/*`)은 요청 문장으로 설명되지 않으나, 리뷰 1 응답에서 사람이 "범위 내(a)"로 결정함(`migration-review-1-response.md` "결정 (사람)") → 이슈 #1, #2 는 경고로만 남김. 의존성(`build.gradle`) · `db/` · `legacy/` · `characterization/lib` 변경 없음. |
| 컨벤션 준수 | 충족 | 컨트롤러는 Service 만 주입(`ItemSearchController.java:17-20`), Repository · SQL · EntityManager 직접 사용 없음. 응답은 record DTO(`ItemSearchResponse.java:12`). 빈 catch · `System.out` · `printStackTrace` 없음, SLF4J 사용(`ItemSearchService.java:24`). 새 Controller · Service 에 같은 패키지 테스트 있음(`ItemSearchControllerTest`, `ItemSearchServiceTest`). 시각 사용 없음. |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `docs/assignment/ARCHITECTURE.md:1`, `docs/assignment/BUSINESS-RULES.md:1`, `docs/assignment/ERD.md:1` | 다른 모듈(assignment) 문서가 diff 에 포함됨. 요청은 item-bank 엔드포인트 1개 이관. 리뷰 1 에서 사람이 범위 내로 결정했으나 diff 기준(`upstream/main`)은 그대로라 계속 잡힘. | 머지 전에 PR 설명에 "1회차 산출물 포함"을 적거나, 기준 브랜치를 직전 이관 이전으로 옮겨 diff 를 줄임. |
| 2 | 경고 | `CLAUDE.md:1`, `.claude/skills/convention-check/SKILL.md:1`, `.claude/skills/document-module/SKILL.md:1` | 팀 규칙 · Skill 추가는 이관 요청과 무관. #1 과 같은 이유로 diff 에 섞임. | #1 과 동일. |
| 3 | 제안 | `docs/verify/migration-review-1.md:1`, `docs/verify/migration-review-1-response.md:1` | 이관 산출물이 아니라 리뷰 기록. 이 회차에서 새로 diff 에 들어옴. | 같은 PR 에 둘지 사람이 결정. |
| 4 | 제안 | `docs/item-bank/ARCHITECTURE.md:1`, `docs/item-bank/BUSINESS-RULES.md:1`, `docs/item-bank/CROSS-CHECK.md:1`, `docs/item-bank/ERD.md:1` | 코드 주석이 `BR-05` 등으로 참조(`ItemSearchCondition.java:15`)해 연관은 있으나 문서화 산출물. 리뷰 1 #3 과 같은 지적. | 포함 여부를 사람이 결정. |
| 5 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:57-60` | `byId::get` 이 null 이면 `ItemSearchRow.from` 에서 NPE. 두 조회가 같은 읽기 전용 트랜잭션 안이라 가능성은 낮음(리뷰 1 #4, 응답에서 "수용 · 수정 없음"). | null 행을 건너뛸지, 의도적 실패로 명시할지 결정. |

## 레거시 의심 동작 (이관 이슈 아님, 고치지 않음)
- 난이도 미지정 시 `level < 5` 라 난이도 5 문항이 기본 검색에서 빠짐. 레거시 주석은 "1~5 모두 포함"(`legacy/item-bank-php/search.php:213-215`). 이관 코드도 같게 동작(`ItemSearchCondition.java:40-41`, `ItemSearchRepository.java:28`).
- LIKE 에서 `%` · `_` 를 이스케이프하지 않아 와일드카드로 동작(`ItemSearchCondition.java:74-77`, BR-07).

## 확인 필요
- 요청 범위 충족은 리뷰 1 응답의 사람 결정(a)에 의존한다. 이 결정이 이번 머지에도 유효한지 사람이 확인해 주세요. 유효하지 않다면 #1 · #2 를 반려 사유로 올려야 한다.
- 8080 서버는 2026-10-06 12:49 기동(PID 의 시작 시각)이고 `modern/` 의 마지막 변경 커밋 `b7f79f8` 은 12:47 이라 HEAD 코드로 기동된 것으로 보인다. 다만 기동 시점의 작업 트리 상태를 직접 확인하지는 못했다.
- `.claude/skills/verify/` 는 미추적 파일이라 이 diff 에 포함되지 않았다. 커밋 대상인지 사람이 결정.
