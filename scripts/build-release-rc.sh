#!/usr/bin/env bash
# A7-2：构建、对齐并签名 CausalGuard 发布候选 APK，输出 commit SHA 与 APK SHA-256。
#
# 前置：
#   - 已安装 JDK 17、Android SDK（build-tools 含 apksigner/zipalign）
#   - release keystore 与密码（默认读取 $HOME/causalguard-release/ 下的 keystore.properties）
#     可用环境变量覆盖：KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
#   - 第三方底座 submodule：git submodule update --init --recursive
#
# 用法：
#   scripts/build-release-rc.sh [输出目录]
# 默认输出目录：$HOME/causalguard-release/<versionName>
#
# 说明：release keystore 与密码不得入库（.gitignore 已覆盖 *.jks/*.keystore/keystore.properties）。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if [[ -f "$HOME/tools/android-env.sh" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/tools/android-env.sh"
fi
: "${JAVA_HOME:?JAVA_HOME 未设置}"
: "${ANDROID_HOME:?ANDROID_HOME 未设置}"

VERSION_NAME="$(sed -n "s/.*versionName '\([^']*\)'.*/\1/p" app/build.gradle | head -n1)"
VERSION_CODE="$(sed -n 's/.*versionCode \([0-9]*\).*/\1/p' app/build.gradle | head -n1)"
OUT_DIR="${1:-$HOME/causalguard-release/${VERSION_NAME}}"
BUILD_TOOLS_DIR="$ANDROID_HOME/build-tools/${BUILD_TOOLS_VERSION:-37.0.0}"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"
ZIPALIGN="$BUILD_TOOLS_DIR/zipalign"
: "${APKSIGNER:?找不到 apksigner，请检查 build-tools}"
: "${ZIPALIGN:?找不到 zipalign，请检查 build-tools}"

KS_PROPS="$HOME/causalguard-release/keystore.properties"
if [[ -f "$KS_PROPS" ]]; then
  # shellcheck disable=SC1090
  source "$KS_PROPS"
fi
KEYSTORE_FILE="${KEYSTORE_FILE:?缺少 KEYSTORE_FILE（keystore.properties 或环境变量）}"
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:?缺少 KEYSTORE_PASSWORD}"
KEY_ALIAS="${KEY_ALIAS:-causalguard}"
KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"

echo "==> 构建 release APK（未签名）"
./gradlew :app:assembleRelease --console=plain

SRC_APK="app/build/outputs/apk/release/app-release-unsigned.apk"
[[ -f "$SRC_APK" ]] || { echo "未找到 $SRC_APK" >&2; exit 1; }

mkdir -p "$OUT_DIR"
ALIGNED="$OUT_DIR/causalguard-${VERSION_NAME}-aligned-unsigned.apk"
SIGNED="$OUT_DIR/causalguard-${VERSION_NAME}.apk"

echo "==> zipalign"
"$ZIPALIGN" -p -f 4 "$SRC_APK" "$ALIGNED"

echo "==> apksigner 签名"
"$APKSIGNER" sign \
  --ks "$KEYSTORE_FILE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEYSTORE_PASSWORD" --key-pass "pass:$KEY_PASSWORD" \
  --out "$SIGNED" "$ALIGNED"

echo "==> 校验签名"
"$APKSIGNER" verify --verbose --print-certs "$SIGNED"

COMMIT_SHA="$(git rev-parse HEAD)"
SHORT_SHA="$(git rev-parse --short=12 HEAD)"
APK_SHA256="$(sha256sum "$SIGNED" | awk '{print $1}')"

echo
echo "================ CausalGuard Release Candidate ================"
echo "versionName : $VERSION_NAME"
echo "versionCode : $VERSION_CODE"
echo "commit SHA  : $COMMIT_SHA"
echo "short SHA   : $SHORT_SHA"
echo "APK path    : $SIGNED"
echo "APK SHA-256 : $APK_SHA256"
echo "=============================================================="
