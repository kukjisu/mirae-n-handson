# 이관 검증 리뷰 3 — `upstream/main...HEAD -- modern characterization`

요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
대상: `legacy/item-bank-php/search.php` → `GET /api/items/search` (HEAD `adb4910`)
비교 범위: `upstream/main...HEAD` 중 `modern` · `characterization` 경로만(12개 파일). 리뷰 2(`3593687`) 이후 이 경로의 변경은 없다(`git diff --stat 3593687 HEAD -- modern characterization` 결과가 비어 있음).
절차: `/verify` Skill. 체크리스트 대조는 reviewer, 테스트 실행은 tester Sub-agent 가 맡았고, 최종 판정은 메인 세션이 했다.

변경 파일 분류
- `modern/api` (10개): `ItemSearch{Condition,Controller,Repository,Response,Row,Service}.java`, `ItemSearch{Condition,Controller,Repository,Service}Test.java`
- `characterization` (2개): `tests/item-bank.test.js`, `__snapshots__/item-bank.test.js.snap`. 둘 다 `c8f68de`(레거시 베이스라인)에서 추가됐다. 이관 코드(`cab640f`)보다 먼저 들어왔고, 그 뒤로 수정되지 않았다.
- `modern/web` · 그 밖: 변경 없음

## 판정: 승인

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `cd modern/api && ./gradlew test --rerun-tasks`: 46 통과 · 0 실패 · 0 skip. `cd characterization && npm test`(레거시 8081): 31 통과 · 0 실패. `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test`(새 API): 30 통과 · 0 실패 · 1 skip. skip 은 `tests/example-units.test.js:21` 의 `it.skipIf` 로, 원래 레거시에서만 도는 테스트다. 실패한 테스트는 없다. 새 public 메서드에는 대응 테스트가 있다(`ItemSearchControllerTest.java:34,54`, `ItemSearchServiceTest.java:43,65,82`). 테스트를 지우거나 skip 한 곳은 없다. |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. 비밀값 리터럴 없음. 조회 전용이다(`@Transactional(readOnly = true)`). `ItemSearchRepository.java:22-40` 은 JPQL 파라미터 바인딩이고, 정렬은 화이트리스트다(`ItemSearchCondition.java:124-142`). 공개 문항 조건 `status = 'A'` 을 유지한다(`ItemSearchRepository.java:25`). `legacy/` 변경 없음. `characterization/` 의 테스트 · 스냅샷은 이 이관에서 수정되지 않았다. |
| 요청 범위 이탈 없음 | 충족 (지정 경로 기준) | 요청 문장과 변경 파일 12개를 대조했다. 모두 search.php 이관이거나 그 안전망(동작 보존 테스트)이다. `build.gradle` · `package.json` · `db/` · 설정 파일 변경은 없다. 지정 경로 밖 파일은 "확인 필요" 1번을 본다. |
| 컨벤션 준수 | 충족 | 컨트롤러는 Service 만 주입한다(`ItemSearchController.java:17-20`). 응답은 record DTO 다. 빈 catch · `System.out` · `printStackTrace` 없음. 로그는 SLF4J 만 쓴다. 포맷팅 경고 1건(#3)이 있지만, 경고는 체크리스트상 기준 판정에 넣지 않는다. |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchCondition.java:88-91` | 같은 이름의 배열 파라미터와 단일 파라미터가 섞이면 결과가 레거시와 다르다. PHP 는 `$_GET` 을 파싱할 때 나중 키가 앞 값을 덮어써서 `q[]=a&q=b` 를 "b" 로 받는다(`legacy/item-bank-php/search.php:54-55`). Java 는 `name[]` 를 항상 먼저 봐서 "a" 를 쓴다. `q[0]=x` 도 PHP 는 "x", Java 는 "" 다. 스냅샷 17건에는 이 경우가 없다. | 파라미터가 들어온 순서를 반영해 마지막 키가 이기게 맞춘다. 맞추지 않을 거면 "의도된 차이"로 문서화하고 사람의 승인을 받는다. 동작 보존 케이스를 추가할지도 함께 정한다. |
| 2 | 경고 | `modern/api/src/main/java/com/example/common/GlobalExceptionHandler.java:53` (대응 레거시 `legacy/item-bank-php/search.php:720-728`) | DB 오류 경로가 다르다. 레거시는 예외를 잡아 HTTP 200 에 "검색 중 오류가 발생했습니다." 를 보여 준다. modern 은 `Exception` 핸들러를 타고 오류 응답이 된다. 정상 경로가 아니라 동작 보존 테스트로는 잡히지 않는다. | 상태 코드가 바뀌는 것은 동작 변경이다. 이관 PR 에 "의도된 차이"로 적고 승인을 받거나, 레거시처럼 맞출지 사람이 정한다. |
| 3 | 경고 | `modern/api/src/test/java/com/example/item/ItemSearchRepositoryTest.java:44,45,48` | 한 줄이 129 · 128 · 124자다. 기준은 120자 이내다(`templates/CLAUDE.spring.md:45`). | 인자 단위로 줄을 바꾼다. |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:57-60` | id 조회와 엔티티 조회 사이에 문항 상태가 바뀌면 `byId::get` 이 null 을 돌려주고, `ItemSearchRow.from` 에서 NPE 가 난다. 같은 읽기 전용 트랜잭션 안이라 가능성은 낮다(리뷰 1 #4 · 리뷰 2 #5 와 같은 지적). | null 행을 건너뛰고 WARN 로그를 남길지, 의도된 실패로 명시할지 정한다. |
| 5 | 제안 | `modern/api/src/test/java/com/example/item/ItemSearchServiceTest.java:25` | Mockito 단위 테스트에 붙은 `@ActiveProfiles("test")` 는 효과가 없다. | 제거한다. 동작 영향은 없다. |

## 레거시 의심 동작 (이관 이슈 아님, 고치지 않음)

- 난이도를 비우면 `level < 5` 라서 난이도 5 문항이 기본 검색에서 빠진다. 레거시 주석은 "1~5 모두 포함"이다(`legacy/item-bank-php/search.php:213-215`). 이관 코드도 같게 동작한다(`ItemSearchCondition.java:40-41`).
- 형식이 틀린 단원 · 등록되지 않은 태그 · 범위 밖 난이도에 대해 경고만 내고 그대로 조회해서 0건이 된다(`search.php:111-116,223-229,258-260`). modern 은 이 경고 문구를 응답에 넣지 않는다. 경고는 정규화 대상이 아니라 비교되지 않는다(`characterization/tests/item-bank.test.js:6`). 빠진 기능으로 볼지 사람이 판단해야 한다.
- LIKE 에서 `%` · `_` 를 이스케이프하지 않아 와일드카드로 동작한다(`search.php:94-97`).
- 페이지 번호가 마지막 페이지를 넘어도 보정하지 않는다. 결과는 빈 표이고 안내 문구도 없다(`search.php:345-349,657`). 스냅샷 09 에 그대로 남아 있다.

## 확인 필요

- 지정 경로 밖 변경이 있다. 경로 제한 없이 `upstream/main...HEAD` 를 보면 파일 33개가 바뀌었다(`CLAUDE.md`, `.claude/skills/*`, `.mcp.json`, `dbhub.toml`, `docs/**`, `templates/**`, `mcp-skeleton/src/index.ts`). 이 브랜치를 통째로 머지한다면 체크리스트 3-2 에 따라 반려 대상이다. 이 판정은 `modern` · `characterization` 만 머지 단위로 본다는 전제에서 유효하다. 머지 단위를 사람이 확인해 주세요.
- 대괄호 파라미터: 인코딩되지 않은 `q[]=a` 를 내장 Tomcat 이 400 으로 거절하는지 실행해서 확인하지 않았다. 거절한다면 레거시(200)와 다르다. #1 과 함께 확인해야 한다.
- `phpIntval`: trim 이 지우지 않는 `\f` 같은 문자로 시작하는 난이도 값을 PHP 7.4 `(int)` 와 똑같이 처리하는지 실행해서 확인하지 않았다.
- 8080 서버는 2026-10-06 13:19:46 에 기동했고, `modern/` 의 마지막 변경 커밋은 `cab640f` 이며 작업 트리에 `modern/` 변경은 없다. 그래서 HEAD 코드로 기동된 것으로 보인다. 다만 빌드 산출물이 HEAD 와 같은지를 직접 대조하지는 않았다.
- 리뷰 시점에 `.claude/agents/`, `.claude/settings.json`, `.claude/skills/rca/` 는 미추적이고 `.claude/skills/verify/SKILL.md` 는 수정만 된 상태(미커밋)라, 이 diff 에 포함되지 않았다.
