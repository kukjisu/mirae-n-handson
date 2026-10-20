# 실전 프로젝트 설계 — 데이터 · 인프라(IaC) / kukjisu

## 1. 목표
Terraform 변경 요청 1건을 Claude 가 작성하고, 읽기 전용 `iac-reviewer` 가 위험 변경을 검토한 기록을 파일로 남기는 흐름을 한 번 끝까지 돌린다.

## 2. 재료
| 재료 | 있음/없음 | 경로 |
|---|---|---|
| IaC 리뷰어 Sub-agent(읽기 전용) | 있음 | `.claude/agents/iac-reviewer.md` |
| 더미 Terraform | 있음 | `pipeline-samples/terraform/main.tf` · `variables.tf` |
| Terraform 작성 규칙 | 있음 | `CLAUDE.md` 의 "Terraform 규칙" 절 |
| 검증 Skill | 있음 | `.claude/skills/verify/SKILL.md` |
| 안전장치 | 있음 | `.claude/settings.json`(terraform apply · destroy deny), `hooks/block-dangerous.mjs` · `cloud-change-guard.mjs` |
| IaC 사이클 Skill(3회차 초안) | 있음 · 오늘은 쓰지 않음 | `.claude/skills/iac-change/SKILL.md` |
| 리뷰 결과 파일 | 없음 → 새로 만들 것 | `docs/project/iac-review.md` |

## 3. 범위
### 한다
1. `pipeline-samples/terraform/main.tf` 에 리소스 **하나**를 추가한다 — 문항 은행 API 서버용 보안 그룹 `aws_security_group.itembank_api`.
2. 그 변경을 `iac-reviewer` 로 **한 번** 검토하고 결과를 `docs/project/iac-review.md` 에 남긴다.
3. 변경과 리뷰 기록을 `/verify` 로 확인하고 커밋한다.

### 하지 않는다
- 반려 → 수정 → 재리뷰 → 승인 사이클 — 완성 기준은 "리뷰 1회"다. 반려가 나와도 기록이 있으면 충족되므로 수정은 이후로 미룬다.
- headless 한 줄 실행 · CI 연동 — 3회차 설계에 있던 항목. 최소 범위 밖이고 40분 안에 끝나지 않는다.
- `iac-change` Skill 완성 · 사용 — 사이클 전체를 다루는 Skill 이라 범위가 크다. 오늘은 메인 대화 생성 + Sub-agent 리뷰로 충분하다.
- 리소스 2개 이상 추가, 기존 RDS · 보안 그룹 수정 — 교체 · 개방 위험이 생기고 리뷰 범위가 커진다.
- `terraform fmt` · `validate` · `plan` · `apply` 실행 — 이 환경에 terraform 이 없고 apply 는 권한 정책상 deny 다. 결과를 추측으로 적지 않는다.
- 실제 AWS 계정 · EKS · EC2 연동 — 실습용 더미 코드만 다룬다.

## 4. 미해결 질문
| 질문 | 누구에게 확인 | 답 |
|---|---|---|
| 실무 AWS provider 버전(v4 / v5) — 리뷰어의 "재생성" 판단 근거가 달라진다 | 인프라 담당(본인 확인) | |
| 실무 보안 그룹 이름 · 태그 규칙 | 인프라 담당 | |
| 리뷰가 반려로 나오면 4-1 안에서 고칠지, 4-3 피어리뷰 뒤로 미룰지 | 본인 | 기본값: 미룸 |

## 5. 단계
| 단계 | 할 일 | 만들거나 고칠 파일 | 확인 방법 |
|---|---|---|---|
| 1단계 | API 서버용 보안 그룹 리소스 1개를 `CLAUDE.md` Terraform 규칙대로 추가 | `pipeline-samples/terraform/main.tf` | `git diff --stat day4-start -- pipeline-samples/terraform/` 에 `main.tf` 한 파일만 보임 · `grep -c 'resource "aws_security_group" "itembank_api"' pipeline-samples/terraform/main.tf` 가 `1` |
| 2단계 | `iac-reviewer` 에 diff 를 넘겨 1회 검토, 결과 원문을 저장 | `docs/project/iac-review.md` | `head -1 docs/project/iac-review.md` 가 `판정: 승인` 또는 `판정: 반려` · 파일에 `검토자: iac-reviewer` 줄이 있음 |
| 3단계 | `/verify day4-start` 로 변경 확인 후 커밋 | (커밋) | `git log --oneline day4-start..HEAD` 에 구현 커밋이 보임 · `git status --short pipeline-samples/terraform/ docs/project/` 이 비어 있음 |

## 6. 완성 기준 (Day 4-1에서 점검)
- [ ] `git diff day4-start -- pipeline-samples/terraform/main.tf` 에 `resource "aws_security_group" "itembank_api"` 블록 추가가 보이고, 그 밖의 `resource` 블록 변경은 0개다.
- [ ] 같은 diff 의 추가 줄에 `0.0.0.0/0` · `::/0`(80 · 443 제외), 비밀값 `default`, `identifier` 변경이 0건이다(`git diff day4-start -- pipeline-samples/terraform/ | grep -E '^\+.*(0\.0\.0\.0/0|::/0|default *= *"|identifier)'` 출력이 비어 있음).
- [ ] `docs/project/iac-review.md` 첫 줄이 `판정: 승인` 또는 `판정: 반려` 이고, 지적이 있으면 각 지적에 `파일:줄번호` 가 있다.
- [ ] `docs/project/iac-review.md` 에 `검토자: iac-reviewer` 와 검토 일시가 적혀 있다(메인 대화가 아니라 Sub-agent 가 검토했다는 기록).
- [ ] `git status --short pipeline-samples/terraform/ docs/project/` 출력이 비어 있다(변경과 기록이 커밋됨).
