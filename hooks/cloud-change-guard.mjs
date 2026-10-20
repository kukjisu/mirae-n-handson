#!/usr/bin/env node
// hooks/cloud-change-guard.mjs
// PreToolUse(Bash) 훅: EKS · EC2 등 클라우드 변경 명령은 사용자 동의를 받는다.
// - kubectl · helm · eksctl · aws 의 조회 명령은 그대로 통과
// - 그 밖의 변경 · 삭제 명령은 permissionDecision "ask" 로 사람에게 묻는다(exit 0 + stdout JSON)
// - 조회지만 비밀값 원문이 나오는 명령은 exit 2 로 차단한다
import { readFileSync } from "node:fs";

let input;
try {
  input = JSON.parse(readFileSync(0, "utf8")); // fd 0 = stdin
} catch {
  process.exit(0); // 입력을 읽지 못하면 통과(fail-open)
}

if (input.tool_name !== "Bash") process.exit(0);

const command = String(input.tool_input?.command ?? "");
const subcommands = command.split(/&&|\|\||;|\|/).map((part) => part.trim()).filter(Boolean);

// 값을 하나 받는 전역 옵션. 이 옵션 뒤의 토큰은 동사로 보지 않는다.
const VALUE_FLAGS = new Set([
  "-n", "--namespace", "--context", "--kubeconfig", "--cluster", "--user", "-s", "--server",
  "--region", "--profile", "--output", "--query", "--endpoint-url", "--cli-read-timeout",
]);

function words(part) {
  return part.split(/\s+/).filter(Boolean);
}

// 도구 이름 뒤에서 옵션을 건너뛰고 동사(위치 인자)만 순서대로 꺼낸다.
function positionals(tokens) {
  const out = [];
  for (let i = 1; i < tokens.length; i++) {
    const t = tokens[i];
    if (t.startsWith("-")) {
      if (!t.includes("=") && VALUE_FLAGS.has(t)) i++;
      continue;
    }
    out.push(t);
  }
  return out;
}

const KUBECTL_READ = new Set([
  "get", "describe", "logs", "top", "explain", "api-resources", "api-versions",
  "version", "cluster-info", "diff", "wait", "auth",
]);
const KUBECTL_CONFIG_READ = new Set(["view", "get-contexts", "current-context", "get-clusters"]);
const HELM_READ = new Set(["list", "ls", "status", "get", "history", "show", "template", "search", "version", "lint"]);

// 판정: "allow" | "ask" | "deny", 사유
function judge(part) {
  const tokens = words(part);
  const tool = tokens[0];
  const pos = positionals(tokens);

  if (tool === "kubectl") {
    const [verb, resource = ""] = pos;
    if (!verb) return ["allow"];
    if ((verb === "get" || verb === "describe") && /^(secrets?|secret\/)/.test(resource)) {
      return ["deny", `kubectl ${verb} ${resource} 는 Secret 값을 대화에 노출합니다.`];
    }
    if (verb === "config") return KUBECTL_CONFIG_READ.has(pos[1]) ? ["allow"] : ["ask", `kubectl config ${pos[1] ?? ""} 는 접속 대상을 바꿉니다.`];
    if (KUBECTL_READ.has(verb)) return ["allow"];
    return ["ask", `kubectl ${verb} 는 EKS 클러스터를 변경합니다.`];
  }

  if (tool === "helm") {
    const verb = pos[0];
    if (!verb || HELM_READ.has(verb)) return ["allow"];
    return ["ask", `helm ${verb} 는 EKS 클러스터를 변경합니다.`];
  }

  if (tool === "eksctl") {
    const verb = pos[0];
    if (!verb || verb === "get" || verb === "version" || verb === "info") return ["allow"];
    return ["ask", `eksctl ${verb} 는 EKS 클러스터 · 노드그룹을 변경합니다.`];
  }

  if (tool === "aws") {
    const [service = "", op = ""] = pos;
    if (!op) return ["allow"];
    if (service === "secretsmanager" && op === "get-secret-value") {
      return ["deny", "aws secretsmanager get-secret-value 는 비밀값 원문을 대화에 노출합니다."];
    }
    if (service === "ssm" && /^get-parameters?/.test(op) && /--with-decryption/.test(part)) {
      return ["deny", "aws ssm get-parameter --with-decryption 은 비밀값 원문을 대화에 노출합니다."];
    }
    if (/^(describe|get|list)-/.test(op)) return ["allow"];
    if (service === "s3" && op === "ls") return ["allow"];
    if (service === "s3api" && /^(get|list|head)-/.test(op)) return ["allow"];
    if (service === "sts" && op === "get-caller-identity") return ["allow"];
    if (service === "configure" && op === "list") return ["allow"];
    return ["ask", `aws ${service} ${op} 는 AWS 자원(EC2 · EKS · IAM · S3 등)을 변경합니다.`];
  }

  return ["allow"];
}

const asks = [];
for (const part of subcommands) {
  const [decision, reason] = judge(part);
  if (decision === "deny") {
    console.error(`[cloud-change-guard] 차단됨: ${reason}\n실행하려던 명령: ${command}`);
    process.exit(2);
  }
  if (decision === "ask") asks.push(reason);
}

if (asks.length > 0) {
  console.log(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: "PreToolUse",
        permissionDecision: "ask",
        permissionDecisionReason: `[cloud-change-guard] 사용자 동의 필요: ${asks.join(" / ")}`,
      },
    })
  );
}

process.exit(0);
