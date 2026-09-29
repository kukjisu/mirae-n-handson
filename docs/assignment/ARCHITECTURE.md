# assignment-thymeleaf 아키텍처 — 새 배포 화면

대상: `legacy/assignment-thymeleaf` · **범위: "새 배포" 화면(`distribution_form.html`) 하나와, 그 화면을 여는 요청 · 폼이 보내는 요청이 부르는 코드까지.**
범위 밖: 배포 목록(`GET /distributions`), 재배포(`POST /distributions/{id}/redistribute`), 과제 목록(`AssignmentController`). 이 파일들은 범위 확인에 필요한 만큼만 봤다.

경로 표기: `java/…` = `src/main/java/com/example/assign/…`, `templates/…` = `src/main/resources/templates/…`.

## 모듈 개요

- Spring Boot + Spring MVC + Thymeleaf + `spring-boot-starter-jdbc`(JdbcTemplate) 구성이다(build.gradle:25-28). JPA 는 없다.
- 계층: `@Controller` → `@Service` → `@Repository`(DAO, JdbcTemplate 로 SQL 직접 실행) → MariaDB `itembank`.
- 단, **컨트롤러가 DAO 를 직접 주입받아 부른다**(DistributionController.java:23-24, 42-43, 53). 서비스를 거치지 않는 경로가 있다.
- 현재 시각은 `AppClock.now()` 정적 메서드로 얻는다. 시스템 속성 `app.clock=system` 이 없으면 **2026-09-15 00:00:00 고정값**을 돌려준다(AppClock.java:14-19). Dockerfile:11 · docker-compose.yml:50-66 에는 이 속성을 넣는 설정이 없다.
- 컨테이너 포트 8080 → 호스트 8082(docker-compose.yml:53-54).

## 범위 안 파일

```
src/main/java/com/example/assign/
├── AppClock.java                    now() · fmt()
├── web/DistributionController.java  form() · create()        ← 진입점
├── web/DbErrorAdvice.java           DataAccessException → db_error 화면
├── service/DistributionService.java distribute()             (redistribute() 는 범위 밖)
├── dao/AssignmentDao.java           findAllForSelect() · findById()
├── dao/ClassDao.java                findAll() · findById()
├── dao/DistributionDao.java         countByAssignmentAndClass() · nextId() · insert()
└── model/AssignmentRow.java · ClassRow.java
src/main/resources/templates/
├── distribution_form.html           새 배포 화면
└── layout.html                      head · nav · flash · footer 조각
src/main/resources/application.properties   DB 접속 · Hikari(max 4, timeout 5s)
```

## 1. 진입점

| 경로 | 메서드 | 핸들러 | 역할 | 근거 |
|---|---|---|---|---|
| `/distributions/new` | GET | `DistributionController.form(Model)` | 과제 · 학급 선택 목록과 현재 시각을 모델에 담아 `distribution_form` 렌더링 | DistributionController.java:40-47 |
| `/distributions` | POST | `DistributionController.create(assignmentId, classId, RedirectAttributes)` | 폼 제출. 검증 → 배포 저장 → 성공 시 `redirect:/distributions`, 실패 시 flash `error` 와 함께 `redirect:/distributions/new` | DistributionController.java:49-77 |
| (전역) | — | `DbErrorAdvice.dbError(DataAccessException, Model)` | 핸들러 밖으로 나온 `DataAccessException` 을 `db_error` 화면으로 바꾼다 | DbErrorAdvice.java:8-17 |

- 폼은 `th:action="@{/distributions}"` · `method="post"` 로 두 필드 `assignmentId` · `classId` 를 보낸다(distribution_form.html:8, 11, 23).
- 화면 진입 링크: `layout.html` 의 nav "새 배포"(layout.html:26).
- POST 성공 후 이동하는 `GET /distributions`(DistributionController.java:32-38)는 범위 밖이다.

## 2. 의존 관계

### 2-1. 포함 (주입 · 템플릿 조각)

| 포함하는 쪽 | 포함되는 것 | 방식 | 근거 |
|---|---|---|---|
| DistributionController | DistributionService, AssignmentDao, ClassDao | 생성자 주입 | DistributionController.java:22-30 |
| DistributionController | AppClock | import(정적 호출) | DistributionController.java:3 |
| DistributionService | DistributionDao, AssignmentDao, ClassDao | 생성자 주입 | DistributionService.java:28-36 |
| AssignmentDao · ClassDao · DistributionDao | JdbcTemplate | 생성자 주입 | AssignmentDao.java:15-19, ClassDao.java:14-18, DistributionDao.java:17-21 |
| distribution_form.html | layout :: head('새 배포') | th:replace | distribution_form.html:3 → layout.html:3 |
| distribution_form.html | layout :: nav(${menu}) | th:replace | distribution_form.html:5 → layout.html:23 |
| distribution_form.html | layout :: flash | th:replace | distribution_form.html:7 → layout.html:28-34 |
| distribution_form.html | layout :: footer | th:replace | distribution_form.html:32 → layout.html:35 |

### 2-2. 호출 ("A → B" = A 가 B 를 부른다)

**GET /distributions/new**

| 호출 | 호출하는 줄 |
|---|---|
| DistributionController.form → AssignmentDao.findAllForSelect | DistributionController.java:42 |
| DistributionController.form → ClassDao.findAll | DistributionController.java:43 |
| DistributionController.form → AppClock.now | DistributionController.java:44 |
| AssignmentDao.findAllForSelect → JdbcTemplate.query (SELECT assignment ⟕ unit) | AssignmentDao.java:35-37 |
| ClassDao.findAll → JdbcTemplate.query (SELECT class) | ClassDao.java:21 |
| distribution_form.html → `#temporals.format`(Thymeleaf 유틸) | distribution_form.html:17 |

**POST /distributions**

| 호출 | 호출하는 줄 |
|---|---|
| DistributionController.create → AssignmentDao.findById | DistributionController.java:53 |
| DistributionController.create → AppClock.now | DistributionController.java:58 |
| DistributionController.create → AppClock.fmt | DistributionController.java:62 |
| DistributionController.create → DistributionService.distribute | DistributionController.java:67 |
| DistributionService.distribute → AssignmentDao.findById (두 번째 조회) | DistributionService.java:48 |
| DistributionService.distribute → ClassDao.findById | DistributionService.java:52 |
| DistributionService.distribute → DistributionDao.countByAssignmentAndClass | DistributionService.java:59 |
| DistributionService.distribute → DistributionDao.nextId | DistributionService.java:62 |
| DistributionService.distribute → AppClock.now | DistributionService.java:63 |
| DistributionService.distribute → DistributionDao.insert | DistributionService.java:63 |
| AssignmentDao.findById → JdbcTemplate.query | AssignmentDao.java:42-43 |
| ClassDao.findById → JdbcTemplate.query | ClassDao.java:25 |
| DistributionDao.countByAssignmentAndClass → JdbcTemplate.queryForObject | DistributionDao.java:48-50 |
| DistributionDao.nextId → JdbcTemplate.queryForObject | DistributionDao.java:55 |
| DistributionDao.insert → JdbcTemplate.update | DistributionDao.java:60-62 |

**오류 경로**

| 호출 | 호출하는 줄 |
|---|---|
| DbErrorAdvice.dbError → System.out.println (로그) | DbErrorAdvice.java:13 |
| DbErrorAdvice.dbError → `db_error` 뷰 반환 | DbErrorAdvice.java:16 |

### 2-3. 요청 한 번의 순서 (POST)

```
create()                                          DistributionController.java:49
 ├─ assignmentDao.findById(assignmentId)          :53   ← 트랜잭션 밖
 ├─ null 이면 → flash error, redirect new         :54-57
 ├─ AppClock.now() · 마감 검사                     :58-65
 └─ try distributionService.distribute()          :66-67  @Transactional(Service:46)
      ├─ assignmentDao.findById   (다시 조회)       Service:48
      ├─ classDao.findById                         Service:52
      ├─ status 'D' 검사                            Service:56
      ├─ countByAssignmentAndClass                  Service:59
      ├─ nextId  (MAX(id)+1)                        Service:62
      └─ insert                                     Service:63
    성공 → flash message, redirect /distributions  :68, :76
    IllegalArgument/IllegalState → flash error      :69-71
    DataAccessException → flash "DB 오류…"          :72-74
```

## 미확인

- `@Transactional`(DistributionService.java:46)이 실제로 트랜잭션을 여는지: `@EnableTransactionManagement` 나 트랜잭션 매니저 빈 선언은 코드에 없다. `spring-boot-starter-jdbc`(build.gradle:27)의 자동 구성에 기대는 것으로 **추정**하며, 실행으로 확인하지 않았다.
- `DbErrorAdvice` 가 GET `/distributions/new` 의 DB 오류와 POST 의 `findById`(트랜잭션 · try 밖, DistributionController.java:53) DB 오류를 받는다는 것은 `@ControllerAdvice` 의 일반 동작에 기댄 **추정**이다. `db_error.html` 은 범위 밖이라 내용을 분석하지 않았다.
- `@RequestParam long` 에 빈 값 · 숫자가 아닌 값이 오면 400 이 나는지(스프링 기본 동작)는 실행으로 확인하지 않았다. 화면에서는 `required`(distribution_form.html:11, 23)가 빈 값 제출을 막는다.
- `app.clock` 시스템 속성을 컨테이너 밖(예: `JAVA_TOOL_OPTIONS`)에서 넣는지: Dockerfile · docker-compose.yml 에는 없음을 확인했고, 그 밖의 설정은 확인하지 않았다.
