# assignment-thymeleaf ERD · 데이터 흐름 — 새 배포 화면

대상: `legacy/assignment-thymeleaf` · **범위: "새 배포" 화면(`GET /distributions/new` · `POST /distributions`)이 읽고 쓰는 테이블만.** 스키마 근거는 `db/mariadb/init/01-schema.sql`.
`submission` · `item` · `tag` 등은 이 화면이 건드리지 않아 뺐다.

## 테이블 목록

| 테이블 | 용도 | 스키마 근거 | 이 화면에서 |
|---|---|---|---|
| `assignment` | 과제 | 01-schema.sql:82-90 | 읽기 |
| `unit` | 단원(과제 옆에 코드 표시) | 01-schema.sql:11-18 | 읽기 |
| `class` | 학급 | 01-schema.sql:75-80 | 읽기 |
| `distribution` | 배포(과제 × 학급) | 01-schema.sql:92-103 | 읽기 · **쓰기(INSERT)** |

뷰 · 저장 프로시저는 쓰지 않는다.

### 눈여겨볼 컬럼

| 컬럼 | 정의 | 비고 |
|---|---|---|
| `assignment.status` | `CHAR(1) NOT NULL DEFAULT 'O'` — 주석 `O=진행 C=마감` (01-schema.sql:87) | 코드는 `X`(연장) · `D`(삭제)도 쓴다(AssignmentDao.java:29, 36 · DistributionController.java:61 · DistributionService.java:56). 스키마에 CHECK 없음 |
| `assignment.due_at` | `DATETIME NOT NULL` (01-schema.sql:86) | 코드의 `getDueAt() != null` 검사(DistributionController.java:60)는 스키마상 항상 참 |
| `distribution.id` | `INT NOT NULL`, PK, **AUTO_INCREMENT 아님** (01-schema.sql:93, 98) | 앱이 `MAX(id)+1` 로 채번(DistributionDao.java:55-56) |
| `distribution.(assignment_id, class_id)` | 각각 FK · 일반 인덱스, **UNIQUE 없음** (01-schema.sql:99-102) | 중복은 앱에서 COUNT 로 막는다(DistributionDao.java:46-51) |
| `distribution.redistributed` | `TINYINT NOT NULL DEFAULT 0` (01-schema.sql:97) | 새 배포는 0 으로 넣는다(DistributionDao.java:61) |

## 관계

```mermaid
erDiagram
    unit ||--o{ assignment : "assignment.unit_id → unit.id (FK)"
    assignment ||--o{ distribution : "distribution.assignment_id → assignment.id (FK)"
    class ||--o{ distribution : "distribution.class_id → class.id (FK)"

    unit {
        INT id PK
        VARCHAR code UK
        VARCHAR name
    }
    assignment {
        INT id PK
        VARCHAR title
        INT unit_id FK
        DATETIME due_at
        CHAR status "O C (코드: X D)"
    }
    class {
        INT id PK
        VARCHAR name
        VARCHAR teacher_id
    }
    distribution {
        INT id PK "MAX+1 채번"
        INT assignment_id FK
        INT class_id FK
        DATETIME distributed_at
        TINYINT redistributed
    }
```

| 관계 | FK (스키마) | 코드의 조인 조건 |
|---|---|---|
| assignment → unit | fk_assignment_unit (01-schema.sql:89) | `LEFT JOIN unit u ON u.id = a.unit_id` (AssignmentDao.java:24) |
| distribution → assignment | fk_distribution_assignment (01-schema.sql:101) | 상관 서브쿼리 `WHERE d.assignment_id = a.id` (AssignmentDao.java:23) · `WHERE assignment_id = ?` (DistributionDao.java:49) |
| distribution → class | fk_distribution_class (01-schema.sql:102) | `WHERE ... class_id = ?` (DistributionDao.java:49) |

## 테이블별 읽기 · 쓰기 위치

| 테이블 | 종류 | SQL 요지 | 위치 | 어느 요청 |
|---|---|---|---|---|
| assignment, unit, distribution | SELECT | `BASE_SELECT ... WHERE a.status IN ('O','X','C') ORDER BY a.due_at ASC, a.id ASC` (class_cnt 서브쿼리가 distribution 을 읽음) | AssignmentDao.java:21-24, 34-38 | GET |
| class | SELECT | ``SELECT id, name, teacher_id FROM `class` ORDER BY id ASC`` | ClassDao.java:21 | GET |
| assignment, unit, distribution | SELECT | `BASE_SELECT WHERE a.id = ?` — **POST 한 번에 두 번 실행** | AssignmentDao.java:41-48 (호출: DistributionController.java:53, DistributionService.java:48) | POST |
| class | SELECT | ``SELECT ... FROM `class` WHERE id = ?`` | ClassDao.java:25 | POST |
| distribution | SELECT | `SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND class_id = ?` | DistributionDao.java:48-50 | POST |
| distribution | SELECT | `SELECT COALESCE(MAX(id), 0) FROM distribution` | DistributionDao.java:55 | POST |
| distribution | **INSERT** | `INSERT INTO distribution (id, assignment_id, class_id, distributed_at, redistributed) VALUES (?, ?, ?, ?, 0)` | DistributionDao.java:60-62 | POST |

UPDATE · DELETE 는 이 화면 범위에 없다(`markRedistributed` 는 재배포 전용, DistributionDao.java:65-67).

## 요청 → 테이블 흐름

**GET /distributions/new**
1. assignment(+unit, +distribution 서브쿼리) 에서 상태 O · X · C 과제를 마감 빠른 순으로 읽는다 → `assignments`.
2. class 전체를 id 순으로 읽는다 → `classes`.
3. `AppClock.now()` → `now`. 템플릿이 `dueAt` 과 `now` 를 비교해 선택지를 비활성화한다(distribution_form.html:16). DB 접근 없음.

**POST /distributions** (`assignmentId`, `classId`)
1. assignment 조회(트랜잭션 밖, DistributionController.java:53). 없으면 종료.
2. 마감 검사 — DB 접근 없음(DistributionController.java:60-65).
3. 이하 `@Transactional distribute()`(DistributionService.java:46-68):
   assignment 재조회 → class 조회 → distribution COUNT → distribution MAX(id) → distribution INSERT 1행.
4. 쓰기 결과: `distribution` 에 `(MAX+1, assignmentId, classId, AppClock.now(), 0)` 한 행.

## 시드와 맞춰 본 상태 (01-schema 가 아니라 02-seed.sql 기준)

`AppClock` 고정값 2026-09-15 00:00:00 기준(AppClock.java:9):

| 과제 id | due_at · status (02-seed.sql:120-125) | 화면 선택지 |
|---|---|---|
| 1 | 2026-08-20 · C | 비활성 `[마감]` |
| 2 | 2026-08-28 · C | 비활성 `[마감]` |
| 3 | 2026-09-10 · O | 비활성 `[마감]` |
| 4 | 2026-09-25 · O | 선택 가능 |
| 5 | 2026-10-02 · O | 선택 가능 |
| 6 | 2026-09-30 · O | 선택 가능 |

시드에 `X` · `D` 상태 과제는 없다. 이미 있는 배포 조합(02-seed.sql:131-138)과 겹치는 선택(예: 과제 4 × 학급 3)은 중복 오류가 난다. 이 표는 코드 · 시드를 읽어서 만든 것이고 화면을 띄워 확인하지 않았다.
