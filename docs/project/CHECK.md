# 완성 기준 점검 — 데이터 · 인프라(IaC) / kukjisu

1. 트랙 완성 기준 ① IaC 생성: 충족
   증거(실행한 명령과 결과 한 줄): `git diff --stat day4-start -- pipeline-samples/terraform/` → `main.tf | 23 +++`, 1 file changed (aws_security_group.itembank_api 1개 추가)
2. 트랙 완성 기준 ② 리뷰 1회: 충족
   증거(파일 경로 또는 PR 주소): docs/project/iac-review.md — 첫 줄 `판정: 승인`, `검토자: iac-reviewer (Sub-agent)`, 2026-10-20 12:32 KST
3. /verify 판정: 승인
   증거(판정 요약 한 줄, 치명 건수): `/verify day4-start..HEAD` → 판정: 승인, 치명 0건 (경고 2 · 제안 2). 테스트는 "해당 없음" — Terraform 변경만 있어 실행할 테스트가 없고, terraform 미설치로 fmt · validate 미실행

범위를 줄였는가: 예 — 뺀 항목과 이유는 DESIGN.md "3. 범위 — 하지 않는다"에 있음
팀 저장소에 가져갈 파일: 없음 (iac-reviewer · Hook 은 3회차 PR #3 으로 올림)
