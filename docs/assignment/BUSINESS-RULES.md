# assignment-thymeleaf 비즈니스 규칙 후보 — 새 배포 화면

대상: `legacy/assignment-thymeleaf` · **범위: "새 배포" 화면(`GET /distributions/new` · `POST /distributions`)과 그 호출 코드.** 재배포(`redistribute`) 규칙은 범위 밖이다.
경로 표기는 ARCHITECTURE.md 와 같다. 서비스를 띄우지 않고 코드만 읽었다 — 확신도 "확실"은 조건문 · SQL 로 확인했다는 뜻이지 실행 확인이 아니다.

## 선택지 구성 (GET)

### BR-01
- 규칙: 과제 선택지에는 상태가 `O` · `X` · `C` 인 과제만 나오고, 마감 빠른 순 → id 순으로 정렬된다(`D` 등 그 밖의 상태는 목록에서 빠진다).
- 근거: AssignmentDao.java:34-38, DistributionController.java:42
- 근거 코드:
  ```java
      public List<AssignmentRow> findAllForSelect() {
          String sql = BASE_SELECT
                  + " WHERE a.status IN ('O', 'X', 'C') "
                  + " ORDER BY a.due_at ASC, a.id ASC";
          return jdbc.query(sql, MAPPER);
      }
  ```
- 확신도: 확실
- 비고: 과제 목록 화면용 `findAllForList` 는 `a.status <> 'D'` 로 거른다(AssignmentDao.java:29). 두 조건은 O · X · C 밖의 값(예: 소문자, 다른 문자)에서 결과가 갈린다. 비교 대소문자는 BR-13 참고.

### BR-02
- 규칙: 과제 선택지는 마감이 현재 시각보다 이전이고 상태가 `X` 가 아니면 비활성(`disabled`)이 되고, 마감이 지났으면 표시 문구 끝에 `X` 는 ` [연장]`, 그 밖은 ` [마감]` 이 붙는다.
- 근거: distribution_form.html:14-18
- 근거 코드:
  ```html
              <option th:each="a : ${assignments}"
                      th:value="${a.id}"
                      th:disabled="${a.dueAt != null and a.dueAt.isBefore(now) and a.status != 'X'}"
                      th:text="${a.id} + '. ' + ${a.title} + ' (' + ${a.unitCode} + ', 마감 ' + ${#temporals.format(a.dueAt, 'MM-dd HH:mm')} + ')'
                               + (${a.dueAt != null and a.dueAt.isBefore(now)} ? (${a.status == 'X'} ? ' [연장]' : ' [마감]') : '')"></option>
  ```
- 확신도: 확실
- 비고: 화면 쪽 제한이라 우회 가능하다. 서버는 BR-05 에서 같은 조건을 다시 검사한다. 표시 형식: `<id>. <제목> (<단원코드>, 마감 MM-dd HH:mm)`. 단원이 없으면 `unitCode` 가 null(LEFT JOIN, AssignmentDao.java:24)인데 그때 표시가 어떻게 되는지는 확인하지 않았다(스키마상 `unit_id` 는 FK · NOT NULL 이라 드묾). 주석(distribution_form.html:13)과 코드는 일치.

### BR-03
- 규칙: 학급 선택지에는 `class` 테이블 전체가 id 오름차순으로 나오고, `<이름> (<교사ID>)` 로 표시된다. 교사 · 과제에 따른 필터는 없다.
- 근거: ClassDao.java:21, distribution_form.html:25
- 근거 코드:
  ```java
          return jdbc.query("SELECT id, name, teacher_id FROM `class` ORDER BY id ASC", MAPPER);
  ```
  ```html
              <option th:each="c : ${classes}" th:value="${c.id}" th:text="${c.name} + ' (' + ${c.teacherId} + ')'"></option>
  ```
- 확신도: 확실
- 비고: 이미 그 과제가 배포된 학급도 선택지에 그대로 나온다. 중복은 제출 뒤 BR-08 에서 막힌다.

## 검증 (POST) — 코드 실행 순서대로

### BR-04
- 규칙: `assignmentId` 로 과제를 찾지 못하면 flash `error` "존재하지 않는 과제입니다." 를 담아 `/distributions/new` 로 되돌린다.
- 근거: DistributionController.java:53-57
- 근거 코드:
  ```java
          AssignmentRow a = assignmentDao.findById(assignmentId);
          if (a == null) {
              ra.addFlashAttribute("error", "존재하지 않는 과제입니다.");
              return "redirect:/distributions/new";
          }
  ```
- 확신도: 확실
- 비고: 서비스에도 같은 검사가 있지만 메시지에 id 가 붙는다(DistributionService.java:49-51, `"존재하지 않는 과제입니다. (id=" + assignmentId + ")"`). 컨트롤러가 먼저 막으므로 서비스 쪽 메시지는 두 조회 사이에 과제가 지워지는 경우에만 보인다(추정). `findById` 는 상태를 거르지 않으므로 `D` 과제도 여기를 통과한다(AssignmentDao.java:42).

### BR-05
- 규칙: 과제 마감(`due_at`)이 현재 시각(`AppClock.now()`)보다 이전이고 상태가 `X` 가 아니면 배포를 거부하고, flash `error` 에 마감 시각(`yyyy-MM-dd HH:mm`)과 과제 제목을 담아 `/distributions/new` 로 되돌린다.
- 근거: DistributionController.java:58-65, AppClock.java:12, 22-27
- 근거 코드:
  ```java
          LocalDateTime now = AppClock.now();
          // 마감 지난 과제는 새 배포 불가. 연장(X) 상태만 예외
          if (a.getDueAt() != null && a.getDueAt().isBefore(now)) {
              if (!"X".equals(a.getStatus())) {
                  ra.addFlashAttribute("error", "마감(" + AppClock.fmt(a.getDueAt()) + ")이 지난 과제는 배포할 수 없습니다. [" + a.getTitle() + "]");
                  return "redirect:/distributions/new";
              }
          }
  ```
- 확신도: 확실
- 비고:
  - `isBefore` 는 엄격 비교다. 마감 시각과 현재 시각이 **같으면 배포된다.**
  - 상태 `C`(스키마 주석상 "마감", 01-schema.sql:87)라도 마감이 미래면 이 검사를 통과한다. 상태 `C` 자체로 막는 코드는 이 화면에 없다.
  - 마감이 지난 `D` 과제는 BR-07 의 "삭제된 과제" 메시지가 아니라 이 마감 메시지를 받는다(이 검사가 먼저 실행됨).
  - 마감이 지난 과제 + 없는 학급 조합이면 BR-06 이 아니라 이 메시지가 나온다.
  - 이 검사는 `@Transactional distribute()` 밖에서 한다(ARCHITECTURE.md 2-3).
  - `getDueAt() != null` 분기는 스키마상 `due_at NOT NULL`(01-schema.sql:86)이라 늘 참이다.
  - 주석(:59)과 코드 일치.

### BR-06
- 규칙: `classId` 로 학급을 찾지 못하면 "존재하지 않는 학급입니다. (id=<classId>)" 오류를 flash `error` 로 담아 `/distributions/new` 로 되돌린다.
- 근거: DistributionService.java:52-55, DistributionController.java:69-71
- 근거 코드:
  ```java
          ClassRow c = classDao.findById(classId);
          if (c == null) {
              throw new IllegalArgumentException("존재하지 않는 학급입니다. (id=" + classId + ")");
          }
  ```
  ```java
          } catch (IllegalArgumentException | IllegalStateException e) {
              ra.addFlashAttribute("error", e.getMessage());
              return "redirect:/distributions/new";
  ```
- 확신도: 확실
- 비고: 서비스 예외 메시지가 화면에 그대로 나간다(BR-07 · BR-08 · BR-10 도 같은 경로).

### BR-07
- 규칙: 과제 상태가 `D` 이면 "삭제된 과제는 배포할 수 없습니다." 오류로 되돌린다.
- 근거: DistributionService.java:56-58
- 근거 코드:
  ```java
          if ("D".equals(a.getStatus())) {
              throw new IllegalStateException("삭제된 과제는 배포할 수 없습니다.");
          }
  ```
- 확신도: 확실
- 비고: 선택지에서는 `D` 가 이미 빠지므로(BR-01) 폼을 우회한 요청에서만 닿는다. 마감이 지난 `D` 는 BR-05 에서 먼저 걸리므로 이 메시지는 **마감 전인 `D` 과제**에만 나온다. 학급 검사(BR-06)가 먼저라 없는 학급 + `D` 과제면 학급 메시지가 나온다.

### BR-08
- 규칙: 같은 과제 · 같은 학급 배포가 이미 1건 이상 있으면 "이미 이 학급에 배포된 과제입니다. 다시 내려면 배포 목록에서 재배포를 사용하세요." 오류로 되돌린다.
- 근거: DistributionService.java:59-61, DistributionDao.java:46-52, 01-schema.sql:99-102
- 근거 코드:
  ```java
          if (distributionDao.countByAssignmentAndClass(assignmentId, classId) > 0) {
              throw new IllegalStateException("이미 이 학급에 배포된 과제입니다. 다시 내려면 배포 목록에서 재배포를 사용하세요.");
          }
  ```
  ```java
      // 같은 학급 + 같은 과제 는 한 건만 존재해야 한다 (UNIQUE 제약은 없음, 여기서 막는다)
      public int countByAssignmentAndClass(long assignmentId, long classId) {
          Integer n = jdbc.queryForObject(
                  "SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND class_id = ?",
                  Integer.class, assignmentId, classId);
          return n == null ? 0 : n.intValue();
      }
  ```
- 확신도: 확실
- 비고: `redistributed` 값과 무관하게 센다. DAO 주석은 "한 건만 존재해야 한다"지만 시드에 이미 같은 조합이 2건 있다(과제 2 × 학급 1: 02-seed.sql:133-134). DB 에 UNIQUE 가 없어 동시 요청 두 개가 둘 다 COUNT=0 을 보고 둘 다 INSERT 할 수 있다(추정, 실행 안 함).

## 저장 (POST)

### BR-09
- 규칙: 새 배포 id 는 `distribution` 의 최대 id + 1(비어 있으면 1)이고, `distributed_at = AppClock.now()`, `redistributed = 0` 으로 한 행을 넣는다.
- 근거: DistributionService.java:62-63, DistributionDao.java:54-63, 01-schema.sql:93
- 근거 코드:
  ```java
          long id = distributionDao.nextId();
          int n = distributionDao.insert(id, assignmentId, classId, AppClock.now());
  ```
  ```java
      public long nextId() {
          Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM distribution", Long.class);
          return (max == null ? 0L : max.longValue()) + 1L;
      }

      public int insert(long id, long assignmentId, long classId, LocalDateTime distributedAt) {
          return jdbc.update(
                  "INSERT INTO distribution (id, assignment_id, class_id, distributed_at, redistributed) VALUES (?, ?, ?, ?, 0)",
                  id, assignmentId, classId, Timestamp.valueOf(distributedAt));
      }
  ```
- 확신도: 확실
- 비고: `id` 컬럼은 AUTO_INCREMENT 가 아니다(01-schema.sql:93). 동시 요청이면 같은 id 로 INSERT 해 PK 충돌 → `DataAccessException` → BR-11 의 "DB 오류" 메시지가 날 수 있다(추정). 마감 검사(BR-05)에 쓴 `now` 와 저장에 쓴 `now` 는 별도 호출이지만 고정 시계에선 같은 값이다(BR-12).

### BR-10
- 규칙: INSERT 영향 행 수가 1 이 아니면 "배포 저장에 실패했습니다." 오류로 되돌린다. 성공하면 flash `message` "배포가 등록되었습니다. (배포 #<id>)" 를 담아 `/distributions`(배포 목록)로 보낸다.
- 근거: DistributionService.java:64-67, DistributionController.java:67-68, 76
- 근거 코드:
  ```java
          if (n != 1) {
              throw new IllegalStateException("배포 저장에 실패했습니다.");
          }
          return id;
  ```
  ```java
              long id = distributionService.distribute(assignmentId, classId);
              ra.addFlashAttribute("message", "배포가 등록되었습니다. (배포 #" + id + ")");
  ```
  ```java
          return "redirect:/distributions";
  ```
- 확신도: 확실
- 비고: flash 는 layout 의 `flash` 조각이 `.msg` / `.err` 로 그린다(layout.html:28-30).

### BR-11
- 규칙: `distribute()` 안에서 `DataAccessException` 이 나면 원인과 무관하게 "DB 오류로 배포에 실패했습니다." 로 `/distributions/new` 에 되돌린다. 그 밖(GET 전체, POST 의 첫 `findById`)에서 난 `DataAccessException` 은 `db_error` 화면으로 가고, 예외 클래스명과 원인 메시지를 표준 출력에 찍고 화면 모델 `detail` 에도 원인 메시지를 담는다.
- 근거: DistributionController.java:72-75, DbErrorAdvice.java:11-17
- 근거 코드:
  ```java
          } catch (DataAccessException e) {
              ra.addFlashAttribute("error", "DB 오류로 배포에 실패했습니다.");
              return "redirect:/distributions/new";
          }
  ```
  ```java
      @ExceptionHandler(DataAccessException.class)
      public String dbError(DataAccessException e, Model model) {
          System.out.println("[DB-ERROR] " + e.getClass().getSimpleName() + " : " + e.getMostSpecificCause().getMessage());
          model.addAttribute("menu", "");
          model.addAttribute("detail", e.getMostSpecificCause().getMessage());
          return "db_error";
      }
  ```
- 확신도: 추정 (catch 블록은 확실. "try 밖의 DB 오류가 DbErrorAdvice 로 간다"는 `@ControllerAdvice` 일반 동작에 기댄 것이고 실행으로 확인 안 함)
- 비고: POST 의 catch 경로는 로그를 남기지 않는다(메시지만 flash). DbErrorAdvice 경로는 DB 원인 메시지가 화면 모델로 나간다 — `db_error.html` 이 `detail` 을 실제로 출력하는지는 범위 밖이라 확인하지 않았다. `IllegalArgumentException | IllegalStateException` · `DataAccessException` 외의 런타임 예외는 이 핸들러가 잡지 않는다.

## 시각 · 형 변환

### BR-12
- 규칙: 현재 시각은 JVM 시스템 속성 `app.clock` 이 정확히 `"system"` 일 때만 실제 시각(나노초 0)이고, 그 밖(속성 없음 포함)에는 `2026-09-15 00:00:00` 고정값이다.
- 근거: AppClock.java:9, 14-20, Dockerfile:11
- 근거 코드:
  ```java
      private static final String FIXED = "2026-09-15 00:00:00";
  ```
  ```java
      public static LocalDateTime now() {
          String sys = System.getProperty("app.clock");
          if (sys != null && sys.equals("system")) {
              return LocalDateTime.now().withNano(0);
          }
          return LocalDateTime.parse(FIXED, FMT);
      }
  ```
- 확신도: 확실 (코드). 컨테이너에서 고정값이 쓰인다는 것은 Dockerfile:11 · docker-compose.yml:50-66 에 속성이 없음을 본 것이라 추정.
- 비고: 이 값이 GET 의 선택지 비활성화(BR-02), POST 의 마감 검사(BR-05), 저장 시각(BR-09)에 모두 쓰인다. 주석(AppClock.java:8)은 "2024-03 QA 기간 중 임시로 고정"이라 하지만 값은 2026-09-15 다 — "주석만 있는 내용" 참고. 비교는 `equals` 라 `"SYSTEM"` 등 대소문자가 다르면 고정값이다.

### BR-13
- 규칙: "연장" 판정은 Java 에서 `"X".equals(status)` 로 대소문자까지 정확히 `X` 일 때만 참이다. 반면 선택지 SQL 의 `IN ('O', 'X', 'C')` 는 컬럼 콜레이션(`utf8mb4_unicode_ci`)을 따른다.
- 근거: DistributionController.java:61, AssignmentDao.java:36, 01-schema.sql:87, 90
- 근거 코드:
  ```java
              if (!"X".equals(a.getStatus())) {
  ```
  ```java
                  + " WHERE a.status IN ('O', 'X', 'C') "
  ```
- 확신도: 추정
- 비고: `_ci` 콜레이션이면 소문자 `x` 과제가 선택지에는 나오지만(SQL) 연장으로는 인정되지 않을(Java) 것으로 짐작한다. 실행으로 확인하지 않았다. 템플릿의 `a.status != 'X'` · `a.status == 'X'`(distribution_form.html:16, 18)는 SpEL 비교인데, 문자열에서 Java `equals` 와 같게 동작하는지 확인하지 않았다.

## 주석만 있는 내용

코드로 뒷받침되지 않거나 코드와 다른 주석. 규칙으로 올리지 않았다.

| 위치 | 주석 | 코드와의 관계 |
|---|---|---|
| 01-schema.sql:87 | `status ... -- O=진행 C=마감` | 코드는 `X`(연장, DistributionController.java:59-61) · `D`(삭제, DistributionService.java:56)도 쓴다. 스키마 주석에 없음 |
| AppClock.java:8 | "2024-03 QA 기간 중 임시로 고정. 운영 반영 전 LocalDateTime.now() 로 되돌릴 것 (아직 안 되돌림)" | 고정값은 2026-09-15(AppClock.java:9). 되돌리지 않은 상태이며 `app.clock=system` 우회 스위치가 있다 |
| DistributionDao.java:46 | "같은 학급 + 같은 과제 는 한 건만 존재해야 한다" | 앱은 새 배포에서만 막는다(BR-08). 시드에 이미 2건인 조합이 있다(02-seed.sql:133-134) |
| distribution_form.html:13 | "마감 지난 과제는 선택 불가 (연장 상태 제외)" | 코드와 일치(BR-02) |
| DistributionController.java:59 | "마감 지난 과제는 새 배포 불가. 연장(X) 상태만 예외" | 코드와 일치(BR-05) |
| layout.html:35 | "과제 배포 관리 v1.4.2" (푸터 문자열) | 버전 표기일 뿐 동작 없음 |

## 표본 점검 추천

| # | 유형 | 규칙 | 확인 방법 |
|---|---|---|---|
| 1 | 가장 그럴듯한 규칙 | **BR-05** 마감 지난 과제는 배포 불가(연장 제외) | `docker compose --profile thymeleaf up -d` 뒤 `curl -si -X POST -d 'assignmentId=3&classId=1' http://localhost:8082/distributions` → `Location: …/distributions/new` 인지, 따라간 화면에 "마감(2026-09-10 23:59)이 지난 과제는 배포할 수 없습니다. [소수의 곱셈 확인]" 이 나오는지. 이어서 상태 `C` · 마감 미래 과제가 통과하는지는 시드에 없으므로 DistributionController.java:60-61 을 열어 `status` 가 조건에 `X` 로만 쓰이는지 본다 |
| 2 | 형 변환 · 기본값 · NULL | **BR-13** `X` 대소문자 · 콜레이션 차이 (+ BR-12 시계 기본값) | MariaDB 에서 `SELECT 'x' IN ('O','X','C') COLLATE utf8mb4_unicode_ci;` 결과가 1 인지 확인 → 1 이면 소문자 `x` 과제는 선택지에 나오되 DistributionController.java:61 에서 연장으로 인정되지 않는다. (쓰기 실험은 `app` 계정 · 스키마 변경이 필요하니 사전 승인 후) |
| 3 | 부수 효과 | **BR-11** DB 오류 시 flash 메시지 vs `db_error` 화면 + 표준 출력 로그 | DbErrorAdvice.java:13-15 와 DistributionController.java:72-74 를 나란히 열어 로그 유무 차이를 확인하고, 서비스를 띄운 뒤 `docker compose --profile thymeleaf stop mariadb` 상태에서 `curl -s http://localhost:8082/distributions/new` → `docker compose --profile thymeleaf logs assignment` 에 `[DB-ERROR]` 줄이 찍히는지 본다 |

## 검증 이력

| 날짜 | 방법 | 대상 | 판정 | 근거 | 문서 변경 |
|---|---|---|---|---|---|
| | | | | | |
