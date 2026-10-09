#!/usr/bin/env bash
# CausalGuard 助手脚本：把团队对 TrackerControl 底座的补丁应用到固定 commit。
#
# 用法：
#   ./scripts/apply-trackercontrol-hook.sh            # 应用全部补丁（A4-3 + A5-1）
#   ./scripts/apply-trackercontrol-hook.sh --revert   # 撤销全部补丁（回到固定 commit 原状）
#
# 说明：
# - 底座源码以 git submodule 固定在 third_party/tracker-control-android/（commit 9504d41b）。
# - 补丁见 third_party/patches/：
#     a4-3-serversinkhole-network-hook.patch —— 网络事件广播桥接（logPacket/dnsResolved 发事件）
#     a5-1-domain-block-receiver.patch      —— 域名阻断控制通道（ordered broadcast 回执）
# - 登记见 THIRD_PARTY_NOTICES.md。
# - 应用后：底座的 ServiceSinkhole 会 (1) 通过显式包名广播（setPackage("com.causalguard")）
#   发出脱敏连接/DNS 事件；(2) 经 CausalGuardDomainBlockReceiver 接收 ACTION_BLOCK_DOMAIN
#   ordered broadcast，把域名写入运行期阻断集并按 RESULT_OK/CANCELED 诚实回执。
# - 两个补丁均不改动 native 核心；撤销后 submodule 回到未修改状态。

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SUBMODULE="$ROOT/third_party/tracker-control-android"
PIN_COMMIT="9504d41b9f6fa1509d784e5503c084d4b428307d"
# 按依赖顺序应用；A5-1 只依赖 A4-3 已存在的基础结构（均独立于 native 核心）。
PATCHES=(
  "$ROOT/third_party/patches/a4-3-serversinkhole-network-hook.patch"
  "$ROOT/third_party/patches/a5-1-domain-block-receiver.patch"
)
MODE="${1:-apply}"

if [ ! -e "$SUBMODULE/.git" ]; then
  echo "未找到底座 submodule，请先运行：git submodule update --init --recursive" >&2
  exit 1
fi
for p in "${PATCHES[@]}"; do
  if [ ! -f "$p" ]; then
    echo "未找到补丁文件：$p" >&2
    exit 1
  fi
done

ACTUAL="$(git -C "$SUBMODULE" rev-parse HEAD)"
if [ "$ACTUAL" != "$PIN_COMMIT" ]; then
  echo "警告：底座 commit=$ACTUAL，预期=$PIN_COMMIT；请确认补丁仍适用。" >&2
fi

if [ "$MODE" = "--revert" ]; then
  # 逆序撤销（后应用的先撤）
  for ((i = ${#PATCHES[@]} - 1; i >= 0; i--)); do
    p="${PATCHES[$i]}"
    if git -C "$SUBMODULE" apply --reverse --check "$p" 2>/dev/null; then
      git -C "$SUBMODULE" apply --reverse "$p"
    fi
  done
  echo "已撤销 CausalGuard 补丁（A4-3 + A5-1），submodule 回到固定 commit 原状。"
  exit 0
fi

applied=0
for p in "${PATCHES[@]}"; do
  if git -C "$SUBMODULE" apply --reverse --check "$p" 2>/dev/null; then
    echo "补丁已应用，跳过：$p"
    continue
  fi
  git -C "$SUBMODULE" apply --check "$p"
  git -C "$SUBMODULE" apply "$p"
  echo "已应用：$p"
  applied=1
done

if [ "$applied" = "0" ]; then
  echo "全部补丁均已应用，无变更。"
else
  echo "CausalGuard 补丁应用完成（A4-3 + A5-1）。"
fi
echo "下一步：在底座工程构建 fdroid debug，安装后由 CausalGuard（com.causalguard）跨进程接收事件并下发域名阻断。"
