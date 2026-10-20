---
name: iac-change
description: Terraform 변경 요청 1건을 받아 코드를 고치고, iac-reviewer Sub-agent 리뷰 → 반려 시 수정 → 재리뷰 사이클을 돌린 뒤 사이클 기록을 남긴다. 인프라 변경 요청을 받았을 때, IaC 생성 · 리뷰 사이클을 돌려 달라고 할 때 사용한다.
argument-hint: "[변경 요청 한 문장]"
---

# IaC 변경 사이클

요청: $ARGUMENTS (비어 있으면 무엇을 바꿀지 묻고 멈춘다)
대상 폴더: pipeline-samples/terraform/ (main.tf · variables.tf 만 고친다. 새 파일을 만들지 않는다)

## 절차
1. 시작 전 `git status --short pipeline-samples/terraform/` 이 비어 있는지 본다. 비어 있지 않으면 멈추고 알린다.
2. **생성**: 요청대로 main.tf · variables.tf 를 고친다. CLAUDE.md 의 "Terraform 규칙"을 지키려고 애쓰지만, 요청이 규칙과 부딪쳐도 이 단계에서는 요청을 우선하고 부딪친 점을 메모한다(리뷰가 잡는지 보려는 것).
3. **1차 리뷰**: `git diff pipeline-samples/terraform/` 결과를 iac-reviewer Sub-agent 에 넘긴다. 판정과 지적 표를 요약하지 않고 받는다.
4. **수정**: 1차가 반려면 지적 표의 "안전한 대안"대로만 고친다. 요청 의도를 살릴 수 있으면 살리고, 살릴 수 없으면 그 이유를 기록한다.
5. **재리뷰**: 다시 diff 를 iac-reviewer 에 넘긴다. 승인이면 끝. 반려면 4~5를 한 번 더 한다. **수정은 최대 2회.** 그래도 반려면 멈추고 사람에게 넘긴다.
6. **기록**: 아래 형식으로 docs/project/iac-cycles/<YYYY-MM-DD>-<요청 요약 영문 kebab>.md 를 만든다.
7. **원복**: 기록을 저장한 뒤 `git checkout -- pipeline-samples/terraform/` 로 되돌리고 `git status --short pipeline-samples/terraform/` 이 비었는지 보여 준다.

## 규칙
- terraform 명령(plan · apply · destroy 등)은 실행하지 않는다. 결과를 추측해 적지 않는다.
- 판정은 iac-reviewer 의 것만 쓴다. 메인 대화가 판정을 바꾸거나 덧붙이지 않는다.
- 기록과 대화에 비밀값 원문을 적지 않는다. `****` 로 가린다.

## 기록 형식
```
# IaC 사이클 — <요청 요약>
- 일시: <YYYY-MM-DD>
- 요청: <원문>

## 1. 생성 diff
<git diff 결과, 비밀값은 ****>

## 2. 1차 판정
<iac-reviewer 결과 원문>

## 3. 수정 diff
<수정 후 git diff 결과>

## 4. 최종 판정
<iac-reviewer 결과 원문>

## 5. 요약
| 회차 | 판정 | 주요 지적(파일:줄번호) |
|---|---|---|
```
