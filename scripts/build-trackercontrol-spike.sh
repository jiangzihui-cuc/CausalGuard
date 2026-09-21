#!/usr/bin/env bash
# A1-1 助手脚本：在本地构建机固定版本构建 TrackerControl（不导入本仓库）
#
# 用法：
#   ./scripts/build-trackercontrol-spike.sh [WORKDIR]
# 默认工作目录：$HOME/trackercontrol-spike
#
# 说明：
# - 第三方源码保留在仓库外部，只登记固定 commit（见 THIRD_PARTY_NOTICES.md）。
# - 本脚本只做 clone + 构建 + 校验，不修改本仓库任何文件。
# - 演示建议构建 fdroid flavor，避免 Play 依赖。

set -euo pipefail

REPO_URL="https://github.com/TrackerControl/tracker-control-android.git"
PIN_TAG="2026080501"
PIN_COMMIT="9504d41b9f6fa1509d784e5503c084d4b428307d"
WORKDIR="${1:-$HOME/trackercontrol-spike}"
SRC_DIR="$WORKDIR/tracker-control-android"

echo "== 0. 环境检查 =="
command -v git >/dev/null || { echo "缺少 git"; exit 1; }
command -v java >/dev/null || { echo "缺少 JDK（建议 JDK 17+，按 AGP 9.x 要求）"; exit 1; }
java -version 2>&1 | head -1 || true
if [ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]; then
  echo "警告：ANDROID_HOME / ANDROID_SDK_ROOT 未设置，Gradle 可能找不到 SDK"
fi
echo "ANDROID_HOME=${ANDROID_HOME:-<unset>}"
echo "WORKDIR=$WORKDIR"

echo "== 1. 准备源码（固定 commit） =="
mkdir -p "$WORKDIR"
if [ -d "$SRC_DIR/.git" ]; then
  echo "已存在源码，校验 commit..."
  git -C "$SRC_DIR" fetch --depth 1 origin "$PIN_COMMIT" || true
  git -C "$SRC_DIR" checkout -q "$PIN_COMMIT"
else
  git clone --filter=blob:none "$REPO_URL" "$SRC_DIR"
  git -C "$SRC_DIR" checkout -q "$PIN_COMMIT"
fi

ACTUAL="$(git -C "$SRC_DIR" rev-parse HEAD)"
echo "实际 commit: $ACTUAL"
if [ "$ACTUAL" != "$PIN_COMMIT" ]; then
  echo "commit 与固定版本不一致，终止"; exit 1
fi
echo "tag 期望: $PIN_TAG"
echo "LICENSE 首行: $(head -1 "$SRC_DIR/LICENSE")"

echo "== 2. 构建 fdroid debug =="
cd "$SRC_DIR"
./gradlew --no-daemon assembleFdroidDebug

echo "== 3. 产物 =="
find "$SRC_DIR/app/build/outputs" -name "*.apk" -print || true
echo
echo "完成。请把以下信息回填到 docs/spike-results.md 第 2.3 与第 3 节："
echo " - 主机 OS / JDK / Android SDK / NDK / 是否需要 Rust"
echo " - 构建结果与 APK 路径"
echo " - 演示机型号与 Android 版本、VPN 启动后能否联网"
