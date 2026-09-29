---
name: convention-check
description: 변경된 코드가 CLAUDE.md 의 코딩 컨벤션 · 금지 사항 · Terraform 규칙을 지키는지 점검하고, 위반을 파일:줄번호와 함께 보고한다. "컨벤션 점검", "커밋 전 점검", "리뷰 전에 확인", "규칙 어긴 거 없나 봐 줘" 같은 요청에 쓴다. 코드는 고치지 않는다.
argument-hint: "[경로 ...] (생략하면 git 변경 파일)"
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash(git diff *)
  - Bash(git status *)
---

# 컨벤션 점검

## 원칙
- 코드를 직접 고치지 않는다. 수정 방향만 적는다.
- 근거 라인을 댈 수 없는 지적은 하지 않고 "확인 필요"로 남긴다.

## 1. 점검 대상 정하기
- 인자로 경로를 받았으면(`$ARGUMENTS`) 그 경로만 본다.
- 인자가 없으면 `git status --porcelain` 과 `git diff --name-only HEAD` 로 변경 파일을 모은다. `??`(아직 add 하지 않은 새 파일)도 포함한다.
- 대상이 없으면 "점검할 변경 없음"만 출력하고 끝낸다.

## 2. 점검 항목 (예 = 통과)
1. 컨트롤러(`*Controller.java`)가 `*Repository` 를 주입받지 않고, SQL 문자열 · `JdbcTemplate` · `EntityManager` 를 쓰지 않는가?
2. 빈 catch 블록(`catch (...) { }`, 주석만 있는 경우 포함)이 없는가?
3. `System.out.println` · `System.err.println` · `printStackTrace()` 가 없는가? (로그는 SLF4J 만)
4. 새로 추가된 `*Controller` · `*Service` 마다 `src/test/java` 같은 패키지에 `<클래스명>Test.java` 가 있는가?
5. 비밀값 리터럴이 없는가? `password` · `secret` · `token` · `api_key` 류 이름에 문자열 리터럴 대입, Terraform 변수 `default`, `*.tfvars` 값을 본다.
6. 허락이 필요한 경로를 건드리지 않았는가? 변경 파일에 `legacy/` · `db/` · `build.gradle` · `package.json`(의존성) · `application.yml` 의 Hikari 설정이 있으면 위반이 아니라 "승인 필요"로 표시한다.
7. `pipeline-samples/terraform/` 의 모든 `variable` 에 `description` 이 있고, 비밀값 변수는 `sensitive = true` 이면서 `default` 가 없는가?
8. `pipeline-samples/terraform/` 에서 ingress `cidr_blocks` 에 `0.0.0.0/0` · `::/0` 이 없고(80 · 443 제외), `aws_db_instance` 가 `publicly_accessible = false` · `storage_encrypted = true` 인가?

해당 파일이 대상에 없는 항목은 "해당 없음"으로 적는다.
인자 없이 돌릴 때는 `git diff` 로 추가 · 변경된 줄만 판정한다. 기존 코드의 위반은 "기존"으로 따로 표시한다.

## 3. 출력 형식 (순서 고정)

### 판정 요약
| # | 항목 | 판정 (통과 / 위반 / 승인 필요 / 해당 없음 / 확인 필요) |
|---|---|---|

### 위반 목록
| 파일:줄번호 | 어긴 규칙 (#번호) | 수정 방향 |
|---|---|---|

위반이 없으면 표 대신 "위반 없음"을 적는다. "확인 필요" 항목은 표 아래에 무엇을 더 봐야 하는지 한 줄씩 적는다.

## 추가 후보
- 현재 시각은 `Clock` 빈으로만 얻는가? (`LocalDateTime.now()` 등을 인자 없이 호출하지 않음)
- 응답 DTO(`*Response`)가 record 인가?
- 예외 → HTTP 변환이 `common/GlobalExceptionHandler` 한 곳뿐인가?
