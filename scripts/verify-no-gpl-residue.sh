#!/usr/bin/env bash
# v2.0 阶段 1：校验自研网络底座落地后，仓库已无第三方 GPL 底座残留。
#
# 检查项：
#   1. 无第三方底座 submodule / .gitmodules；
#   2. 运行时代码（app/core-model/rule-engine/net-core/settings.gradle）无底座引用；
#   3. 自研底座模块 net-core 存在且已纳入构建；
#   4. manifest 已声明自研 VpnService；
#   5. 仓库未跟踪密钥/APK/PDF/MP4 等敏感或大文件。
#
# 用法：
#   scripts/verify-no-gpl-residue.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

FAILURES=0
pass() { echo "  [ok]   $*"; }
fail() { echo "  [FAIL] $*"; FAILURES=$((FAILURES + 1)); }

echo "== 1. 无第三方底座 submodule / .gitmodules =="
if [ ! -e .gitmodules ]; then pass "无 .gitmodules"; else fail ".gitmodules 仍存在"; fi
if [ ! -e third_party/tracker-control-android ]; then pass "无 tracker-control submodule"; else fail "submodule 仍存在"; fi

echo "== 2. 运行时代码无第三方底座引用（忽略注释行） =="
HITS="$(grep -rIn --include='*.kt' --include='*.java' --include='*.gradle' \
  -e 'trackercontrol' -e 'tracker-control' -e 'net\.kollnig\.missioncontrol' -e 'eu\.faircode' \
  app/src core-model/src rule-engine/src net-core/src settings.gradle 2>/dev/null \
  | grep -vE ':[0-9]+:[[:space:]]*(//|\*|/\*)' || true)"
if [ -z "$HITS" ]; then
  pass "源码无第三方底座引用"
else
  fail "发现第三方底座引用："
  echo "$HITS" | sed 's/^/         /'
fi

echo "== 3. 自研网络底座模块 =="
if [ -d net-core ]; then pass "net-core 存在"; else fail "net-core 缺失"; fi
if grep -q ":net-core" settings.gradle; then pass "settings.gradle 引入 :net-core"; else fail "settings.gradle 未引入 :net-core"; fi

echo "== 4. 自研 VpnService 声明 =="
if grep -q "android.net.VpnService" app/src/main/AndroidManifest.xml; then
  pass "manifest 声明自研 VpnService"
else
  fail "manifest 未声明自研 VpnService"
fi

echo "== 5. 不跟踪密钥/敏感大文件 =="
TRACKED_BAD="$(git ls-files | grep -E '(secrets\.properties|\.jks$|\.keystore$|\.env$|\.pem$|\.apk$|\.aab$|\.pdf$|\.mp4$)' || true)"
if [ -z "$TRACKED_BAD" ]; then
  pass "未跟踪密钥/APK/PDF/MP4 等文件"
else
  fail "发现被跟踪的敏感/大文件："
  echo "$TRACKED_BAD" | sed 's/^/         /'
fi

echo
if [ "$FAILURES" -eq 0 ]; then
  echo "全部检查通过：仓库已去 GPL 底座。"
else
  echo "存在 $FAILURES 项未通过，请修复后重跑。"
  exit 1
fi
