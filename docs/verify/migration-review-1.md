# 이관 검증 리뷰 1 — `upstream/main...HEAD`

요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관. (코드는 수정하지 않고 보고만 한다.)
대상: `legacy/item-bank-php/search.php` → `GET /api/items/search` (커밋 `c8f68de`, `22d580b`, `cab640f`)

## 판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `cd modern/api && ./gradlew test` → 46 통과 · 0 실패 · 0 skip(결과 XML 집계). `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` → 30 통과 · 0 실패 · 1 skip(`example-units.test.js:21`, `legacyOnly` 조건부 skip, 기존 설계). 레거시 대상 `npm test` → 31 통과 · 0 실패. |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. 비밀값 리터럴 · SQL 문자열 결합 · 삭제/덮어쓰기 코드 없음(JPQL 파라미터 바인딩). |
| 요청 범위 이탈 없음 | **미충족** | 요청은 엔드포인트 1개 이관인데, 변경 22개 파일 중 modern/api 10개와 `characterization/tests/item-bank.test.js` · 스냅샷 외 12개가 요청 문장으로 설명되지 않음 → 이슈 #1. |
| 컨벤션 준수 | 충족 | 컨트롤러는 Service 만 주입(`ItemSearchController.java:17-20`), Repository · SQL 직접 호출 없음. 빈 catch · `System.out` · `printStackTrace` 없음, SLF4J 사용(`ItemSearchService.java:24`). 새 Controller · Service 에 대응 테스트 있음. 의존성 · `db/` 변경 없음. |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `docs/assignment/ARCHITECTURE.md:1`, `docs/assignment/BUSINESS-RULES.md:1`, `docs/assignment/ERD.md:1` | 다른 모듈(assignment) 문서 3개가 이 diff 에 포함됨. 요청은 item-bank 엔드포인트 1개 이관. | 별도 PR 로 분리하거나, 사람이 범위에 포함된다고 확정. |
| 2 | 경고 | `CLAUDE.md:1`, `.claude/skills/convention-check/SKILL.md:1`, `.claude/skills/document-module/SKILL.md:1` | 팀 규칙 · Skill 추가는 이관 요청과 무관. `upstream/main` 에 없어 diff 에 전부 신규로 잡힘. | 이관 PR 에서 분리. |
| 3 | 제안 | `docs/item-bank/*.md` (ARCHITECTURE · BUSINESS-RULES · CROSS-CHECK · ERD) | 이관 근거 문서(코드 주석이 `BR-05` 등으로 참조: `ItemSearchCondition.java:15`)라 연관은 있으나 이관 산출물이 아니라 문서화 산출물. | 같은 PR 에 둘지 사람이 결정. |
| 4 | 제안 | `ItemSearchService.java:57-60` | `byId::get` 이 null 이면(id 조회와 상세 조회 사이 삭제 등) `ItemSearchRow.from` 에서 NPE. 읽기 전용 트랜잭션이라 가능성은 낮음. | null 일 때 건너뛰거나 의도적 실패로 명시. |

## 이관 중 보인 레거시 의심 동작(고치지 않음)
- 난이도 미지정 시 `level < 5` 라 난이도 5 문항이 기본 검색에서 빠짐. 레거시 주석은 "1~5 모두 포함" (`ItemSearchCondition.java:40`, `search.php:213-215`). 동작은 그대로 옮겨져 있어 이관 문제는 아님.
- `%` · `_` 를 이스케이프하지 않아 와일드카드로 동작(`ItemSearchCondition.java:74`, BR-07).

## 확인 필요
- 8080 에서 응답한 서버가 **HEAD 의 코드로 기동된 것인지** 확인하지 못함(프로세스만 확인). 새 API 대상 characterization 통과가 HEAD 를 검증한 것인지 사람이 `bootRun` 재기동 후 재실행으로 확인 필요.
- `./gradlew test` 가 2초에 끝나 캐시(up-to-date)였을 수 있음. 결과 XML 의 46건은 이전 실행분일 수 있어 `--rerun-tasks` 재실행 권장.
- `upstream/main` 이 `1bb4f2f` 라 diff 에는 이전 회차 커밋(문서 · Skill)이 섞임. 범위 이탈 판정은 "이관 PR 의 기준 브랜치가 어디인가"에 따라 달라질 수 있음 — 기준을 직전 이관 이전으로 잡으면 #1 ~ #3 은 해소.
