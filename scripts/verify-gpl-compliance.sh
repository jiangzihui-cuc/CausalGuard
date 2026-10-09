#!/usr/bin/env bash
# A8-4：核对 GPL 对应源码、许可证与 tag 的一致性（可复现、失败即非零退出）。
#
# 检查项：
#   1. 网络底座 submodule 存在且 HEAD == 固定 commit；
#   2. 底座根 LICENSE 存在且为 GPL-3.0；
#   3. 团队补丁与 apply 脚本存在且 SHA-256 与登记一致；
#   4. 补丁可在固定 commit 上干净应用（git apply --check）；
#   5. THIRD_PARTY_NOTICES.md 含底座来源/许可证/commit/补丁登记；
#   6. 仓库未跟踪密钥、APK/PDF/MP4 等敏感或大文件；
#   7. （可选）最终发布 tag：`--tag <name>` 校验 tag 存在、指向 HEAD、且与 versionName 一致。
#
# 用法：
#   scripts/verify-gpl-compliance.sh                 # 检查 1~6
#   scripts/verify-gpl-compliance.sh --tag causalguard-v1.0.0
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

SUBMODULE_REL="third_party/tracker-control-android"
APPLY_SCRIPT="scripts/apply-trackercontrol-hook.sh"
NOTICES="THIRD_PARTY_NOTICES.md"
PIN_COMMIT="$(sed -n 's/^PIN_COMMIT="\([0-9a-f]\{40\}\)".*/\1/p' "$APPLY_SCRIPT" | head -n1)"
EXPECTED_SUBMODULE_TAG="2026080501"

declare -A EXPECTED_PATCH_SHA=(
  ["third_party/patches/a4-3-serversinkhole-network-hook.patch"]="97dec53c117a36da615672068e5aacc6e31570200d442bce02046bd463fbe024"
  ["third_party/patches/a5-1-domain-block-receiver.patch"]="28b7ef52d68ae25c2a5496d839fbe139e0e607c21cc45b5c60d58d22e329d3ad"
)
EXPECTED_APPLY_SHA="6d259922f6d072e4786983c419192a8b1b15d7ffd64ea6b94f77c3ab068da5b3"

TAG=""
if [[ "${1:-}" == "--tag" ]]; then
  TAG="${2:?--tag 需要 tag 名称}"
fi

FAILURES=0
pass() { echo "  [ok]   $*"; }
fail() { echo "  [FAIL] $*"; FAILURES=$((FAILURES + 1)); }

echo "== 1. 网络底座 submodule =="
if [ -e "$SUBMODULE_REL/.git" ]; then
  pass "submodule 存在：$SUBMODULE_REL"
  ACTUAL_COMMIT="$(git -C "$SUBMODULE_REL" rev-parse HEAD)"
  if [ "$ACTUAL_COMMIT" = "$PIN_COMMIT" ]; then
    pass "HEAD == 固定 commit $PIN_COMMIT"
  else
    fail "HEAD=$ACTUAL_COMMIT，预期 $PIN_COMMIT（请 git submodule update --init --recursive）"
  fi
  ACTUAL_TAG="$(git -C "$SUBMODULE_REL" describe --tags --always 2>/dev/null || echo unknown)"
  if [ "$ACTUAL_TAG" = "$EXPECTED_SUBMODULE_TAG" ]; then
    pass "上游 tag == $EXPECTED_SUBMODULE_TAG"
  else
    fail "上游 tag=$ACTUAL_TAG，预期 $EXPECTED_SUBMODULE_TAG"
  fi
else
  fail "submodule 缺失，请运行 git submodule update --init --recursive"
fi

echo "== 2. 底座许可证 =="
LICENSE_FILE="$SUBMODULE_REL/LICENSE"
if [ -f "$LICENSE_FILE" ] && grep -q "GNU GENERAL PUBLIC LICENSE" "$LICENSE_FILE" && grep -q "Version 3" "$LICENSE_FILE"; then
  pass "$LICENSE_FILE 为 GPL-3.0"
else
  fail "$LICENSE_FILE 缺失或非 GPL-3.0"
fi

echo "== 3. 团队补丁与 apply 脚本校验和 =="
for patch in "${!EXPECTED_PATCH_SHA[@]}"; do
  if [ ! -f "$patch" ]; then
    fail "缺少补丁 $patch"
    continue
  fi
  actual="$(sha256sum "$patch" | awk '{print $1}')"
  if [ "$actual" = "${EXPECTED_PATCH_SHA[$patch]}" ]; then
    pass "$patch SHA-256 一致"
  else
    fail "$patch SHA-256=$actual，预期 ${EXPECTED_PATCH_SHA[$patch]}"
  fi
done
if [ -f "$APPLY_SCRIPT" ]; then
  actual="$(sha256sum "$APPLY_SCRIPT" | awk '{print $1}')"
  if [ "$actual" = "$EXPECTED_APPLY_SHA" ]; then
    pass "$APPLY_SCRIPT SHA-256 一致"
  else
    fail "$APPLY_SCRIPT SHA-256=$actual，预期 $EXPECTED_APPLY_SHA"
  fi
else
  fail "缺少 $APPLY_SCRIPT"
fi

echo "== 4. 补丁可干净应用到固定 commit =="
if [ -e "$SUBMODULE_REL/.git" ]; then
  for patch in "${!EXPECTED_PATCH_SHA[@]}"; do
    if git -C "$SUBMODULE_REL" apply --check "$REPO_ROOT/$patch" >/dev/null 2>&1; then
      pass "$(basename "$patch") git apply --check 通过"
    else
      fail "$(basename "$patch") 无法干净应用（submodule 可能已改或 commit 不符）"
    fi
  done
fi

echo "== 5. THIRD_PARTY_NOTICES 登记 =="
if [ -f "$NOTICES" ]; then
  for needle in "TrackerControl Android" "GPL-3.0" "$PIN_COMMIT" \
                "a4-3-serversinkhole-network-hook.patch" "a5-1-domain-block-receiver.patch"; do
    if grep -q "$needle" "$NOTICES"; then
      pass "包含：$needle"
    else
      fail "缺少登记：$needle"
    fi
  done
else
  fail "缺少 $NOTICES"
fi

echo "== 6. 不跟踪密钥/敏感大文件 =="
TRACKED_BAD="$(git ls-files | grep -E '(secrets\.properties|\.jks$|\.keystore$|\.env$|\.pem$|\.apk$|\.aab$|\.pdf$|\.mp4$)' || true)"
if [ -z "$TRACKED_BAD" ]; then
  pass "未跟踪密钥/APK/PDF/MP4 等文件"
else
  fail "发现被跟踪的敏感/大文件："
  echo "$TRACKED_BAD" | sed 's/^/         /'
fi

echo "== 7. 最终发布 tag（可选） =="
if [ -n "$TAG" ]; then
  VERSION_NAME="$(sed -n "s/.*versionName '\([^']*\)'.*/\1/p" app/build.gradle | head -n1)"
  if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    pass "tag $TAG 存在"
    TAG_COMMIT="$(git rev-parse "$TAG^{commit}")"
    HEAD_COMMIT="$(git rev-parse HEAD)"
    if [ "$TAG_COMMIT" = "$HEAD_COMMIT" ]; then
      pass "tag 指向 HEAD ($HEAD_COMMIT)"
    else
      fail "tag 指向 $TAG_COMMIT，当前 HEAD=$HEAD_COMMIT"
    fi
    if [ "$TAG" = "causalguard-v$VERSION_NAME" ]; then
      pass "tag 名称与 versionName ($VERSION_NAME) 一致"
    else
      fail "tag=$TAG 与 versionName=$VERSION_NAME 不一致"
    fi
  else
    fail "tag $TAG 不存在（A8-5 创建最终 tag 后再运行本检查）"
  fi
else
  echo "  [skip] 未传 --tag；最终 tag 由 A8-5 创建后复核"
fi

echo
if [ "$FAILURES" -eq 0 ]; then
  echo "全部检查通过。"
else
  echo "存在 $FAILURES 项未通过，请修复后重跑。"
  exit 1
fi
