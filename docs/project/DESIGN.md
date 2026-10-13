# 실전 프로젝트 설계 — 데이터 · 인프라(IaC) / kukjisu

### 1. 목표
인프라 담당자가 Terraform 변경 요청을 받을 때마다 손으로 하던 "변경 작성 → 위험 검토 → 수정" 반복을, 명령 한 줄로 돌고 기록이 남는 사이클로 줄인다.

### 2. 재료
- 재사용:
  - `.claude/agents/iac-reviewer.md` — 읽기 전용 IaC 리뷰어(Day 3-2)
  - `pipeline-samples/terraform/main.tf` · `variables.tf` — 더미 RDS · 보안 그룹 코드
  - `CLAUDE.md` 의 "Terraform 규칙" 절 — 생성 쪽이 지킬 규칙
  - `.claude/settings.json` — terraform apply · destroy deny, `hooks/block-dangerous.mjs` (Day 3-3 · 3-4)
- 새로 만들 것:
  - `.claude/skills/iac-change/SKILL.md` — 변경 요청을 받아 생성 → `iac-reviewer` 리뷰 → 반려면 수정 → 재리뷰를 돌리고 기록을 남기는 Skill
  - `docs/project/iac-cycles/<날짜>-<요청이름>.md` — 사이클 1회 기록(요청 · 생성 diff · 1차 판정 · 수정 diff · 최종 판정)

### 3. 범위
- 한다:
  1. `iac-change` Skill: 요청 1건 → 생성 → `iac-reviewer` 리뷰 → 반려 시 수정 → 재리뷰(최대 2회)
  2. headless 한 줄 실행: `claude -p "/iac-change <요청>"` 로 같은 사이클이 돈다
  3. 사이클 기록 형식을 고정하고 1회분 기록을 남긴다
- 하지 않는다:
  - `terraform plan` · `apply` 실행 — 이 환경에 terraform 이 없고, apply 는 권한 정책상 deny 다. 판정은 diff 와 provider 동작 근거로만 한다
  - 실제 AWS 계정 · EKS 클러스터 · ECR 연동 — 실습 저장소는 더미 코드만 다룬다
  - Helm · Kubernetes 매니페스트 리뷰 — 업무에는 필요하지만 리뷰 기준을 새로 세워야 해서 4회차 안에 끝나지 않는다
  - GitHub Actions 연동(PR 마다 자동 실행) — Day 3-1 워크플로와 합치면 범위가 두 배가 된다. headless 한 줄까지만 한다
  - 비용 추정 자동화 — `CLAUDE.md` 의 "비용 영향 한 줄" 규칙으로 갈음한다

### 4. 미해결 질문
| 질문 | 누구에게 확인 | 답 |
|---|---|---|
| 실무 Terraform 의 AWS provider 버전(v4 / v5)은? identifier 변경이 재생성인지 이름 변경인지가 달라진다 | 인프라 담당(본인 확인) | |
| 실무에서 보안 그룹 0.0.0.0/0 예외로 허용하는 포트가 80 · 443 외에 있는가 | 인프라 담당 · 보안 담당 | |
| 반려 후 자동 수정을 몇 회까지 허용할지(현재 가정: 2회, 넘으면 사람에게 넘김) | 본인 | |
| 사이클 기록을 팀 저장소에도 올릴지, 개인 기록으로만 둘지 | 팀 반영 담당 | |

### 5. 단계
| 단계 | 만들 것 | 확인 방법 | 상태 |
|---|---|---|---|
| 1단계 | `.claude/skills/iac-change/SKILL.md` (생성 → `iac-reviewer` → 수정 → 재리뷰 절차, 기록 형식 포함) | `head -5 .claude/skills/iac-change/SKILL.md` 로 frontmatter 확인, `grep -c "iac-reviewer" .claude/skills/iac-change/SKILL.md` 가 1 이상 | 시작 전 |
| 2단계 | 대화형에서 `/iac-change` 1회 실행 → 사이클 기록 1건 | `ls docs/project/iac-cycles/` 에 파일 1개, 그 파일에 "1차 판정: 반려"와 "최종 판정: 승인"이 모두 있음 | 시작 전 |
| 3단계 | headless 한 줄 실행 | `claude -p "/iac-change ..." --output-format json` 의 `is_error` 가 false, 실행 뒤 `git checkout -- pipeline-samples/terraform/` 후 `git status --short pipeline-samples/terraform/` 이 비어 있음 | 시작 전 |

### 6. 완성 기준 (Day 4-1에서 점검)
- [ ] `docs/project/iac-cycles/` 에 사이클 기록 1건이 있고, 요청 → 생성 diff → 1차 판정(반려) → 수정 diff → 최종 판정(승인) 다섯 칸이 모두 채워져 있다
- [ ] 1차 반려 사유에 `파일:줄번호` 가 1개 이상 있고, 그 줄을 `git diff` 로 열었을 때 실제로 그 내용이 있다
- [ ] 최종 승인된 diff 에 `0.0.0.0/0`(80 · 443 제외), 비밀값 `default`, identifier 변경이 하나도 없다(`grep` 으로 확인)
- [ ] `claude -p "/iac-change ..."` 한 줄 실행이 `is_error: false` 로 끝난다
- [ ] 사이클이 끝난 뒤 `git status --short pipeline-samples/terraform/` 이 비어 있다(실습 저장소에 변경을 남기지 않음)

### 7. 안전장치
- 적용되는 권한 · Hook:
  - `.claude/settings.json` deny: `terraform apply` · `terraform destroy`, `rm -rf`, `.env` · `~/.aws/**` 읽기
  - `hooks/block-dangerous.mjs`: `/bin/rm -rf` · `rm -fr` · `DROP TABLE` · `prod-db` 차단
  - `iac-reviewer` 는 `tools: Read, Grep, Glob` 이라 리뷰 단계에서 파일을 고칠 수 없다
- 사람이 확인하는 지점:
  - 2회 수정 뒤에도 반려면 자동으로 더 고치지 않고 사람에게 넘긴다
  - 최종 승인 diff 는 사람이 `git diff` 로 읽은 뒤에만 다음으로 넘긴다
  - 실제 적용(`terraform apply`)은 언제나 사람이 한다
