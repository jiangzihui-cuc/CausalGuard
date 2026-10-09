#!/usr/bin/env bash
# A8-2：打包最终提交用的源码包（不含密钥/真实数据/临时文件）。
#
# 内容：
#   CausalGuard-src-<version>/
#     <主仓库指定 revision 的全部受控文件：app/core-model/rule-engine/demo-app/docs/scripts/...>
#     third_party/tracker-control-android/   网络底座对应源码（submodule 固定 commit 的归档）
#     third_party/patches/*.patch            团队对底座的补丁（对应源码 = 固定 commit + 补丁）
#     SOURCE_MANIFEST.txt                    commit / 版本 / 补丁与许可证校验和
#
# 说明：
# - 使用 `git archive` 只打包**受控文件**，密钥/真实数据/临时文件因 `.gitignore` 不在其中；
# - 底座源码以“固定 commit + 补丁”交付，复现对应源码见 scripts/apply-trackercontrol-hook.sh；
# - 最终提交包应在最终 tag（A8-5）建立后，以该 tag 为 revision 重新生成一次。
#
# 用法：
#   scripts/package-source.sh [revision] [输出目录]
#   revision 默认 HEAD，输出目录默认 build/source-package/
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

REV="${1:-HEAD}"
OUT_DIR="${2:-$REPO_ROOT/build/source-package}"

VERSION_NAME="$(sed -n "s/.*versionName '\([^']*\)'.*/\1/p" app/build.gradle | head -n1)"
: "${VERSION_NAME:?无法从 app/build.gradle 解析 versionName}"

REV_SHA="$(git rev-parse "$REV")"
SHORT_SHA="$(git rev-parse --short=12 "$REV")"
STAGE_NAME="CausalGuard-src-${VERSION_NAME}-${SHORT_SHA}"
STAGE="$OUT_DIR/$STAGE_NAME"
ARCHIVE="$OUT_DIR/$STAGE_NAME.tar.gz"

SUBMODULE_REL="third_party/tracker-control-android"
SUBMODULE_COMMIT="$(git -C "$SUBMODULE_REL" rev-parse HEAD)"

rm -rf "$STAGE"
mkdir -p "$STAGE"

echo "==> 导出主仓库受控文件 @ $REV ($REV_SHA)"
git archive "$REV" | tar -x -C "$STAGE"

echo "==> 导出网络底座对应源码 @ $SUBMODULE_COMMIT"
mkdir -p "$STAGE/$SUBMODULE_REL"
git -C "$SUBMODULE_REL" archive "$SUBMODULE_COMMIT" | tar -x -C "$STAGE/$SUBMODULE_REL"

echo "==> 写 SOURCE_MANIFEST.txt"
{
  echo "CausalGuard 源码包清单"
  echo "生成时间: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "versionName: $VERSION_NAME"
  echo "revision: $REV_SHA (input: $REV)"
  echo "submodule: $SUBMODULE_REL"
  echo "submodule commit: $SUBMODULE_COMMIT"
  echo "submodule tag: $(git -C "$SUBMODULE_REL" describe --tags --always 2>/dev/null || echo unknown)"
  echo
  echo "# 团队补丁 SHA-256（对应源码 = 底座固定 commit + 以下补丁）"
  (cd "$STAGE" && sha256sum third_party/patches/*.patch scripts/apply-trackercontrol-hook.sh)
  echo
  echo "# 第三方许可证文件存在性"
  for f in "$SUBMODULE_REL/LICENSE"; do
    if [ -f "$STAGE/$f" ]; then echo "present: $f"; else echo "MISSING: $f"; fi
  done
  echo
  echo "# 复现对应源码"
  echo "git submodule update --init --recursive"
  echo "scripts/apply-trackercontrol-hook.sh"
} > "$STAGE/SOURCE_MANIFEST.txt"

echo "==> 生成归档 $ARCHIVE"
( cd "$OUT_DIR" && tar czf "$ARCHIVE" "$STAGE_NAME" )

echo
echo "=============== CausalGuard 源码包 ==============="
echo "versionName : $VERSION_NAME"
echo "revision    : $REV_SHA"
echo "目录        : $STAGE"
echo "归档        : $ARCHIVE"
echo "SHA-256     : $(sha256sum "$ARCHIVE" | awk '{print $1}')"
echo "=================================================="
