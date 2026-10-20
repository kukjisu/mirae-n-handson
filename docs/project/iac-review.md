판정: 승인

치명 등급 지적은 없습니다. 이번 변경은 새 보안 그룹 `aws_security_group.itembank_api` 하나만 추가합니다. 기존 리소스는 삭제되거나 수정되지 않습니다.

| 번호 | 심각도 | 파일:줄 | 지적 내용 | 근거 | 권고 |
|---|---|---|---|---|---|
| - | - | - | 지적 없음 | - | - |

**위험 변경별 확인 결과**

- **보안 그룹 개방: 해당 없음.**
  - 들어오는 쪽(main.tf:48-54)은 8080 포트만 `[var.api_subnet_cidr]`에서 받습니다. 이 변수의 기본값은 `10.20.7.0/24`입니다(variables.tf:13-17). `0.0.0.0/0`이나 `::/0`은 쓰지 않았습니다.
  - 나가는 쪽(main.tf:56-62)은 3306 포트만 열고, 대상은 CIDR이 아니라 DB 보안 그룹 ID로 제한합니다.
- **리소스 삭제·교체: 해당 없음.**
  - 추가만 있습니다. `itembank_db`, `aws_db_subnet_group.itembank`, `aws_db_instance.itembank`는 바뀌지 않았습니다.
  - 재생성을 일으키는 속성(`identifier`, `engine`, `name`)도 바뀌지 않았습니다.
  - `publicly_accessible = false`와 `storage_encrypted = true`도 그대로 유지됩니다.
- **시크릿 노출: 해당 없음.** 새 블록에는 비밀번호, 키, 토큰이 없습니다. `db_password`는 여전히 `sensitive = true`이고 `default`도 없습니다(variables.tf:37-41).

**비용 영향:** 없습니다. 보안 그룹은 과금되지 않습니다.

**참고 (판정과 무관):** 아래 두 가지는 위험 기준 밖의 동작 문제입니다. 의도한 설정인지만 확인하면 됩니다.

1. 들어오는 출발지가 API 서버 자신의 서브넷뿐입니다. 로드밸런서나 다른 서브넷의 클라이언트는 8080에 접속할 수 없습니다.
2. 나가는 규칙을 명시해서 AWS 기본 egress(전체 허용)가 사라집니다. API 서버는 DB 3306 외에는 외부로 나갈 수 없게 됩니다. 이미지를 받거나(ECR), 패키지를 내려받거나, AWS API를 호출할 수 없습니다.

또한 기존 DB 보안 그룹의 들어오는 규칙은 아직 서브넷 CIDR 기준입니다. 새로 만든 API 보안 그룹을 출발지로 쓰도록 바꾸는 일은 이번 diff 범위 밖입니다.

검토한 파일:
- pipeline-samples/terraform/main.tf
- pipeline-samples/terraform/variables.tf

---

검토자: iac-reviewer (Sub-agent)
검토 일시: 2026-10-20 12:32 KST
검토 대상: git diff pipeline-samples/terraform/
