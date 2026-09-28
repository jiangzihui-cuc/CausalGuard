#!/usr/bin/env bash
# A4-3 助手脚本：把 CausalGuard 网络广播桥接补丁应用到固定版本的 TrackerControl 底座。
#
# 用法：
#   ./scripts/apply-trackercontrol-hook.sh          # 应用补丁
#   ./scripts/apply-trackercontrol-hook.sh --revert # 撤销补丁（回到固定 commit 原状）
#
# 说明：
# - 底座源码以 git submodule 固定在 third_party/tracker-control-android/（commit 9504d41b）。
# - 补丁内容见 third_party/patches/a4-3-serversinkhole-network-hook.patch，登记见 THIRD_PARTY_NOTICES.md。
# - 应用后底座的 ServiceSinkhole 会在 logPacket/dnsResolved 末尾通过 LocalBroadcastManager
#   发出脱敏连接/DNS 事件，供 CausalGuard Adapter 同进程接收。
# - 该补丁不会修改 native 核心；撤销后 submodule 回到未修改状态。

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SUBMODULE="$ROOT/third_party/tracker-control-android"
PATCH="$ROOT/third_party/patches/a4-3-serversinkhole-network-hook.patch"
PIN_COMMIT="9504d41b9f6fa1509d784e5503c084d4b428307d"
MODE="${1:-apply}"

if [ ! -e "$SUBMODULE/.git" ]; then
  echo "未找到底座 submodule，请先运行：git submodule update --init --recursive" >&2
  exit 1
fi
if [ ! -f "$PATCH" ]; then
  echo "未找到补丁文件：$PATCH" >&2
  exit 1
fi

ACTUAL="$(git -C "$SUBMODULE" rev-parse HEAD)"
if [ "$ACTUAL" != "$PIN_COMMIT" ]; then
  echo "警告：底座 commit=$ACTUAL，预期=$PIN_COMMIT；请确认补丁仍适用。" >&2
fi

if [ "$MODE" = "--revert" ]; then
  git -C "$SUBMODULE" apply --reverse --check "$PATCH"
  git -C "$SUBMODULE" apply --reverse "$PATCH"
  echo "已撤销 A4-3 补丁，submodule 回到固定 commit 原状。"
  exit 0
fi

if git -C "$SUBMODULE" apply --reverse --check "$PATCH" 2>/dev/null; then
  echo "补丁已应用，跳过。"
  exit 0
fi

git -C "$SUBMODULE" apply --check "$PATCH"
git -C "$SUBMODULE" apply "$PATCH"
echo "已应用 A4-3 广播桥接补丁。"
echo "下一步：在底座工程构建 fdroid debug，安装后由 CausalGuard 同进程接收事件。"
