#!/usr/bin/env bash
# A8-3：提交前清理核对（可复现、失败即非零退出）。
#
# 检查项：
#   1. 工作区受控文件未包含密钥/签名/账号/真实数据/原始日志/大文件；
#   2. git 历史中从未新增过上述敏感/大文件（提交包含完整历史）；
#   3. 工作区无非忽略的未跟踪文件（避免误提交临时/敏感文件）；
#   4. .gitignore 覆盖关键密钥与大文件模式。
#
# 用法：
#   scripts/verify-clean-submission.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

PATTERN='(secrets\.properties|keystore\.properties|\.jks$|\.keystore$|\.env$|\.env\..*|\.pem$|\.key$|\.p12$|\.apk$|\.aab$|\.pdf$|\.mp4$|\.log$|\.hprof$|\.tmp$)'

FAILURES=0
pass() { echo "  [ok]   $*"; }
fail() { echo "  [FAIL] $*"; FAILURES=$((FAILURES + 1)); }

echo "== 1. 工作区受控文件 =="
TRACKED="$(git ls-files | grep -E "$PATTERN" || true)"
if [ -z "$TRACKED" ]; then
  pass "未跟踪密钥/签名/真实数据/原始日志/大文件"
else
  fail "发现被跟踪的敏感/大文件："
  echo "$TRACKED" | sed 's/^/         /'
fi

echo "== 2. git 历史新增过的敏感/大文件 =="
HIST="$(git log --all --pretty=format: --name-only --diff-filter=A | sort -u | grep -E "$PATTERN" || true)"
if [ -z "$HIST" ]; then
  pass "历史中从未提交敏感/大文件"
else
  fail "历史中存在敏感/大文件，需从历史清理："
  echo "$HIST" | sed 's/^/         /'
fi

echo "== 3. 非忽略的未跟踪文件 =="
UNTRACKED="$(git status --porcelain --untracked-files=all | awk '/^\?\?/ {print $2}')"
if [ -z "$UNTRACKED" ]; then
  pass "无未跟踪文件"
else
  echo "  [warn] 存在未跟踪文件，提交前请确认其中无密钥/真实数据："
  echo "$UNTRACKED" | sed 's/^/         /'
fi

echo "== 4. .gitignore 关键模式覆盖 =="
for pat in '*.jks' '*.keystore' 'keystore.properties' 'secrets.properties' '*.apk' '*.aab' '*.pdf' '*.mp4' '*.log'; do
  if grep -qF -- "$pat" .gitignore; then
    pass ".gitignore 覆盖 $pat"
  else
    fail ".gitignore 缺少 $pat"
  fi
done

echo
if [ "$FAILURES" -eq 0 ]; then
  echo "清理核对通过。"
else
  echo "存在 $FAILURES 项未通过，请修复后重跑。"
  exit 1
fi
