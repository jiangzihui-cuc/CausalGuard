#!/usr/bin/env bash
# A1-8：抓取 TrackerControl 底座的 UID 归属日志并统计成功率（Linux / macOS）
#
# 用法：
#   ./scripts/capture-uid-attribution.sh [抓取秒数] [输出文件]
#   默认：120 秒，uid-attribution.log
#
# 说明：
#   - 需要已安装 adb（platform-tools），手机已连接并授权 USB 调试
#   - 抓取期间请正常使用手机（微信 / 浏览器 / 购物类 App 等），制造 TCP/UDP 流量
#   - 只统计底座调用 getConnectionOwnerUid 的 TCP/UDP 连接；ICMP 不进入此调用

set -euo pipefail

SECONDS_TO_RUN="${1:-120}"
OUT_FILE="${2:-uid-attribution.log}"

if ! command -v adb >/dev/null 2>&1; then
  echo "找不到 adb。请先安装 Android platform-tools 并加入 PATH。" >&2
  exit 1
fi

echo "已连接设备："
adb devices

echo "清空 logcat 缓冲..."
adb logcat -c

echo "开始抓取 ${SECONDS_TO_RUN} 秒，请在此期间正常使用手机..."
adb logcat -v time -s "TrackerControl.VPN" > "$OUT_FILE" &
LOGCAT_PID=$!
sleep "$SECONDS_TO_RUN"
kill "$LOGCAT_PID" 2>/dev/null || true
wait "$LOGCAT_PID" 2>/dev/null || true

ATTEMPTS=$(grep -c "Get uid local=" "$OUT_FILE" || true)
TOTAL=$(grep -oE "Get uid=-?[0-9]+" "$OUT_FILE" | wc -l | tr -d ' ')
VALID=$(grep -oE "Get uid=-?[0-9]+" "$OUT_FILE" | awk -F= '$2 >= 0' | wc -l | tr -d ' ')
INVALID=$(( TOTAL - VALID ))
if [ "$TOTAL" -gt 0 ]; then
  RATE=$(awk "BEGIN{printf \"%.1f\", 100*$VALID/$TOTAL}")
else
  RATE=0
fi

echo
echo "===== A1-8 UID 归属统计 ====="
echo "getUid 调用（local 行） : $ATTEMPTS"
echo "Get uid= 结果行          : $TOTAL"
echo "成功(uid >= 0)          : $VALID"
echo "失败(uid = -1)          : $INVALID"
echo "成功率                  : ${RATE}%"
echo "原始日志                : $OUT_FILE"
