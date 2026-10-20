#!/usr/bin/env node
// hooks/db-change-guard.mjs
// PreToolUse(Bash, mcp__dbhub__.*) 훅: DB 데이터 삭제 · 변경, 메타데이터 변경은 사용자 동의를 받는다.
// - 대상: DELETE · UPDATE · TRUNCATE · DROP · ALTER · RENAME · CREATE/DROP INDEX · GRANT · REVOKE
// - 로컬 compose 더미 DB(docker compose exec, localhost · 127.0.0.1)는 묻지 않는다(tmpfs 시드라 down → up 으로 복구)
// - DROP TABLE · DROP DATABASE 와 운영 DB(prod-db)는 block-dangerous.mjs 가 따로 차단한다
// 동의 = exit 0 + stdout JSON permissionDecision "ask"
import { readFileSync } from "node:fs";

let input;
try {
  input = JSON.parse(readFileSync(0, "utf8")); // fd 0 = stdin
} catch {
  process.exit(0); // 입력을 읽지 못하면 통과(fail-open)
}

const DANGEROUS_SQL = [
  { name: "DELETE", re: /\bDELETE\s+FROM\b/i },
  { name: "UPDATE", re: /\bUPDATE\s+[`"\w.]+\s+SET\b/i },
  { name: "TRUNCATE", re: /\bTRUNCATE\b/i },
  { name: "DROP", re: /\bDROP\s+(TABLE|DATABASE|SCHEMA|INDEX|VIEW|USER|PROCEDURE|FUNCTION|TRIGGER)\b/i },
  { name: "ALTER", re: /\bALTER\s+(TABLE|DATABASE|SCHEMA|USER|VIEW|INDEX)\b/i },
  { name: "RENAME", re: /\bRENAME\s+(TABLE|USER)\b/i },
  { name: "CREATE INDEX", re: /\bCREATE\s+(UNIQUE\s+)?INDEX\b/i },
  { name: "GRANT", re: /\bGRANT\s+\w/i },
  { name: "REVOKE", re: /\bREVOKE\s+\w/i },
];

// 셸에서 DB 에 쿼리를 보내는 클라이언트
const DB_CLIENT = /(^|[\s/])(mysql|mariadb|psql|sqlcmd|sqlite3|mongosh?)(\s|$)/;
// 로컬 더미 DB 로 보는 조건
// 호스트 이름 뒤가 공백 · 끝 · 포트(:)일 때만 인정한다(127.0.0.1.evil.com 같은 흉내 호스트 제외).
const DUMMY_TARGET = /docker\s+compose\b.*\bexec\b|(-h|--host)[=\s]*(localhost|127\.0\.0\.1)(?=[\s:]|$)/;

function ask(reason) {
  console.log(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: "PreToolUse",
        permissionDecision: "ask",
        permissionDecisionReason: `[db-change-guard] 사용자 동의 필요: ${reason}`,
      },
    })
  );
  process.exit(0);
}

const found = (text) => DANGEROUS_SQL.filter(({ re }) => re.test(text)).map(({ name }) => name);

// MCP DB 도구: 입력 전체에서 SQL 을 찾는다. 대상 호스트를 알 수 없으므로 더미 예외를 두지 않는다.
if (String(input.tool_name ?? "").startsWith("mcp__dbhub")) {
  const hits = found(JSON.stringify(input.tool_input ?? {}));
  if (hits.length > 0) ask(`MCP DB 도구로 ${hits.join(" · ")} 쿼리를 실행합니다.`);
  process.exit(0);
}

if (input.tool_name !== "Bash") process.exit(0);

const command = String(input.tool_input?.command ?? "");
if (!DB_CLIENT.test(command)) process.exit(0); // DB 클라이언트를 부르지 않는 명령(grep 등)은 검사하지 않는다
if (DUMMY_TARGET.test(command) && !/prod[-.]db/.test(command)) process.exit(0);

const hits = found(command);
if (hits.length > 0) ask(`DB 에 ${hits.join(" · ")} 쿼리를 실행합니다(더미 DB 아님).`);

// SQL 파일을 리다이렉트로 넣으면 내용을 볼 수 없으므로 묻는다.
if (/<\s*\S+\.sql\b/.test(command)) ask("SQL 파일을 DB 에 실행합니다. 파일 안의 쿼리를 확인하세요(더미 DB 아님).");

process.exit(0);
