Terraform 변경 1건(`pipeline-samples/terraform/main.tf`)을 Claude 가 작성하고, 읽기 전용 `iac-reviewer` Sub-agent 가 보안 그룹 개방 · 리소스 삭제/교체 · 시크릿 노출을 검토해 `docs/project/iac-review.md` 에 남긴 뒤, `/verify` 로 머지 전 승인/반려를 판정하는 흐름을 자동화한다.
`mirae-n-handson/` 에서 Claude Code 세션을 열고 `/verify day4-start..HEAD` 를 실행한다(상위 폴더에서 열면 `reviewer` · `tester` Sub-agent 가 로드되지 않는다).
이 환경에는 terraform 이 없어 `fmt` · `validate` · `plan` 을 돌리지 않았고 변수값도 더미이므로, 현업에 옮길 때는 실제 AWS provider 버전(v4/v5)과 보안 그룹 이름 · 태그 규칙을 확인하고 `terraform validate` · `plan` 단계를 검증 흐름에 넣어야 한다.
