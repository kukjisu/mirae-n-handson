# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

답변과 문서는 한국어로 쓴다.

## 저장소 성격

Claude Code 심화 과정(4회차)의 **실습 저장소**다. 코드 · 데이터 · 로그는 전부 더미 에듀테크 도메인(문항 은행 · 과제 배포 · 성적 집계)이다. 여러 독립 프로젝트가 한 저장소에 모여 있고, 루트에 공통 빌드는 없다. 명령은 각 하위 폴더에서 실행한다.

- `legacy/` 는 분석 · 이관 **대상**이다. 허락 없이 수정하지 않는다. 레거시 규칙을 옮길 때는 근거를 `파일:줄번호` 로 댄다.
- `modern/` 이 팀 컨벤션의 기준이다. 이관 결과물은 여기에 들어간다.
- README 의 "이 저장소에 넣지 않은 것" 표에 있는 파일(`.claude/`, `hooks/*.mjs`, `.mcp.json`, `characterization/tests/<모듈>.test.js` 와 스냅샷, `modern/api` 의 `/api/items/search` 등)은 참가자가 수업 중에 만든다. 요청 없이 미리 만들지 않는다.
- 도메인 용어: 문항 `item` · 단원 `unit` · 난이도 `level`(1~5) · 태그 `tag` / 학급 `class` · 과제 `assignment` · 배포 `distribution` · 제출 `submission` / 학생 식별자 `STU-<숫자>`.

## 실행 환경 (docker compose 프로필)

| 프로필 | 뜨는 것 | 포트 |
|---|---|---|
| `php` | `legacy/item-bank-php` (PHP 7.4) + MariaDB | 8081, 3306 |
| `thymeleaf` | `legacy/assignment-thymeleaf` (Spring MVC + JDBC) + MariaDB | 8082, 3306 |
| `mssql` | `legacy/grade-mssql` (저장 프로시저 + 얇은 Java 호출부) + MS-SQL | 8083, 1433 |
| `modern` | MariaDB 만. `modern/api`(8080) · `modern/web`(5173)는 로컬 실행 | 3306 |

- `php` · `thymeleaf` · `modern` 은 **MariaDB 서비스 하나**(`itembank` DB)를 공유한다. 데이터는 tmpfs 라 `down` → `up` 하면 시드 상태(고정 ID · 고정 시각)로 돌아간다. 스키마 · 시드는 `db/mariadb/init/`, `db/mssql/init/`.
- `up` · `down` · `build` · `logs` 모두 `--profile` 을 붙여야 한다: `docker compose --profile php up -d`, `docker compose --profile php down`.
- Apple Silicon 에서 `mssql` 은 느리거나 안 뜰 수 있다. 성적 집계 로직은 `legacy/grade-mssql/sql/*.sql` 을 읽으면 된다.
- DB 계정은 더미 · 읽기 전용이다. 값은 README 의 "DB 계정" 표를 참조한다. 쓰기 계정(`app`, `sa`)은 compose 안에서만 쓴다.
- 환경 점검: `bash scripts/check-env.sh`. 서비스 생존 확인: `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081` (200/302 = 떠 있음, 000 = 꺼짐).

## 명령

```bash
# modern/api — Spring Boot 3.3 · Java 21 · Gradle
cd modern/api && ./gradlew test                                   # DB 없이 통과해야 함 (@ActiveProfiles("test") → H2 MariaDB 모드)
cd modern/api && ./gradlew test --tests 'com.example.item.ItemServiceTest'   # 단일 테스트 클래스
cd modern/api && ./gradlew bootRun                                # 먼저 docker compose --profile modern up -d

# modern/web — React 18 · TypeScript · Vite
cd modern/web && npm ci && npm run dev
cd modern/web && npm run lint && npm run typecheck && npm test
cd modern/web && npx vitest run src/components/ItemTable.test.tsx # 단일 테스트

# characterization — 동작 보존(스냅샷) 테스트
cd characterization && npm ci
cd characterization && npm run baseline -- <모듈명>               # item-bank | assignment | grade (폴더명 아님)
cd characterization && npm test                                   # 대상: 레거시 기본 포트
cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test   # 대상: 이관된 새 API
cd characterization && npx vitest run tests/normalize.unit.test.js      # 서비스 없이 도는 단위 테스트

# mcp-skeleton — TypeScript MCP 서버 (SDK v2)
cd mcp-skeleton && npm ci && npx tsc                              # build/index.js 생성

# specs/starters — Day 4-2 스펙 구현용 빈 골격
cd specs/starters/java && ./gradlew test
cd specs/starters/python && source .venv/bin/activate && pytest
```

의존성 설치는 항상 `npm ci` 를 쓴다(`npm install` 은 lockfile 을 바꾼다).

## 금지 사항

- `legacy/` 는 허락 없이 수정하지 않는다.
- `terraform apply` · `destroy` · `import` · `state` 하위 명령 · `force-unlock` 은 실행하지 않는다. 필요하면 답변에 "사람이 실행할 명령"으로만 적는다.
- `*.tfstate*` · `tfplan` · `.env` · `.env.*` · `*.pem` · `~/.aws/` · `~/.ssh/` 는 읽지 않고, 커밋하지 않고, 답변에 내용을 붙이지 않는다.
- 비밀번호 · API 키 · 토큰을 코드 리터럴 · 변수 `default` · `*.tfvars` 에 넣지 않는다.
- 의존성 추가(`build.gradle` · `package.json`), DB 스키마 변경(`db/`), 자원 교체 · 삭제는 작업 전에 먼저 묻는다.
- 요청받지 않은 파일 · 자원을 "정리" 명목으로 고치지 않는다. 변경 파일은 요청 범위 안에 둔다.

## 완료 기준

- 이관 · 리팩토링 작업은 `characterization/` 의 `npm test` 가 전부 통과하기 전에는 완료라고 보고하지 않는다.
- 테스트가 실패하면 실패한 케이스와 차이를 그대로 보고한다. "거의 됐다"는 식으로 요약하지 않는다.
- 테스트를 통과시키려고 `characterization/` 의 테스트 코드나 스냅샷 파일을 고치지 않는다. 스냅샷을 바꿔야 한다고 판단되면 멈추고 묻는다.
- 레거시 동작이 버그로 보여도 이관 중에는 고치지 않는다. "의심 동작" 목록으로 따로 보고한다.

## Terraform 규칙 — `pipeline-samples/terraform/` 에만 적용

실습용 더미 코드다. 실제 AWS 계정에 적용하지 않는다. 이 환경에는 terraform 이 설치되어 있지 않으므로 `fmt` · `validate` · `plan` 결과를 추측으로 적지 않는다.

- 모든 `variable` 에 `description` 을 쓴다. 비밀값 변수는 `sensitive = true` 이고 `default` 가 없다(`db_password` 참고).
- 보안 그룹 ingress 의 `cidr_blocks` 에 `0.0.0.0/0` · `::/0` 을 쓰지 않는다(80 · 443 제외). DB 포트(3306)는 `var.api_subnet_cidr` 처럼 특정 CIDR 로만 연다.
- `aws_db_instance` 는 `publicly_accessible = false` · `storage_encrypted = true` 를 유지한다.
- `identifier` · `engine` · `name` 처럼 자원 교체를 일으키는 속성은 바꾸지 않는다.
- 인스턴스 클래스 · 스토리지 · `multi_az` 처럼 비용이 바뀌는 변경은 답변에 "비용 영향" 한 줄을 적는다.

## 아키텍처 — 여러 파일을 봐야 보이는 것

### 레거시 → 현행 이관 흐름
레거시 3모듈이 `modern/api` 로 이관되는 구조다. 이관의 안전망이 `characterization/` 이다.

- 레거시는 HTML 을, 새 API 는 JSON 을 돌려준다. `characterization/lib/normalize.mjs` 가 둘 다 `{status, rows, count, message}` 로 정규화해서 **같은 스냅샷**과 비교한다. HTML 은 `<table id="items">`(고정 열) · 그 밖의 `<table id>`(머리글 → `HEADER_MAP`) · `<p id="count">` · `<p id="message">` 만 읽고, JSON 은 `VOLATILE_KEYS`(시각 · 세션 · 토큰 등)를 재귀적으로 지운다. `rows` · `tags` 순서는 보존한다(정렬도 동작의 일부).
- 대상 주소는 `lib/target.mjs` 의 `DEFAULT_BASE_URLS` 한 곳에만 있다. 테스트는 `fetchNormalized(모듈명, 레거시경로, 파라미터)` 만 쓰고 주소를 직접 적지 않는다. 이관하며 경로가 바뀌면 `PATH_ALIASES`(예: `/search.php` → `/api/items/search`)에 추가한다 — 레거시 경로가 404 이면 새 경로로 재요청한다.
- 이관 후 비교에서 실패하면 **이관 코드를 고친다.** 테스트 · 스냅샷을 고쳐서 통과시키지 않는다. 흔들리는 값이 스냅샷에 남으면 스냅샷이 아니라 정규화를 고친다. 스냅샷은 `characterization/__snapshots__/` 에 모이고 커밋 대상이다. 규칙 전문은 `characterization/README.md`.

### modern/api
- 도메인 패키지(`com.example.item`, `com.example.assignment`) 안에서 Controller → Service → Repository → 엔티티 순으로만 호출한다. 컨트롤러는 Repository 를 주입받지 않는다. 응답은 record DTO(`*Response`), 예외 → HTTP 변환은 `common/GlobalExceptionHandler` 한 곳.
- 공개 문항만 노출한다: `ItemStatus`(`item.status` CHAR(1), 공개 = `'A'`)로 판단한다.
- 시각은 `common/ClockConfig` 의 `Clock` 빈을 주입받아 쓴다. 테스트는 `Clock.fixed(...)` 로 고정한다(`DistributionServiceTest` 참고).
- `application.yml` 의 Hikari 풀은 운영과 같게 일부러 작다(max 5, connection-timeout 3s). 트랜잭션을 오래 쥐는 코드는 금방 풀을 고갈시킨다. 이 설정은 바꾸지 않는다.
- 컨트롤러는 SQL 문자열 · JdbcTemplate · EntityManager 를 쓰지 않는다.
- catch 블록을 비워 두지 않는다. 로그는 SLF4J 로만 남기고 System.out.println · printStackTrace() 를 쓰지 않는다.
- 새 Controller · Service 의 public 메서드에는 src/test/java 같은 패키지에 대응하는 테스트를 추가한다.

### modern/web
- 모든 API 호출은 `src/api/client.ts` 의 `getJson()` 을 거친다. `API_BASE` 기본값은 `http://localhost:8080`, `VITE_API_BASE=""` 이면 상대 경로 + Vite 프록시(`/api` → 8080)를 쓴다.
- 데이터 로딩은 `src/hooks/useApiQuery.ts` 기반 훅(`useUnits`, `useItem`, `useUnitItems`), 테스트는 `src/test/mockFetch.ts` · `fixtures.ts` 로 fetch 를 모킹한다.

### mcp-skeleton
`@modelcontextprotocol/server@2.0.0` + `zod@4` (SDK **v2**) 기준. 인터넷 예제 대부분은 v1(`@modelcontextprotocol/sdk`)이라 import 경로 · `inputSchema` 모양이 다르다. `src/itemApi.ts` 는 더미 API — 난이도가 DB 의 정수 `level` 과 달리 문자열 `하`/`중`/`상` 이다.

### 그 밖의 폴더 (실습 입력 자료)
- `templates/` — 프로젝트 유형별 `CLAUDE.*.md` 템플릿과 `verification-loop.md` · `approval-checklist.md` · `secure-coding-checklist.md`. `CLAUDE.spring.md` 에 `modern/api` 의 상세 컨벤션(로깅 · 예외 · 테스트 이름 · 금지 사항 · 완료 기준)이 있다.
- `vendor-prs/`(외주 PR 패치), `incident-logs/`(장애 시나리오 a~d), `pipeline-samples/`(배치 로그 · 적재 CSV · Terraform), `ci-ports/`(GitLab CI → 이식용), `specs/`(신규 개발 스펙) — 분석 대상 자료다.
- `scripts/hook-node.sh` — Hook 실행기. node 를 못 찾으면 exit 2 로 막는다(fail-closed). `.claude/settings.json` 의 hook command 에서 `bash "$CLAUDE_PROJECT_DIR"/scripts/hook-node.sh hooks/<이름>.mjs` 로 쓴다.
