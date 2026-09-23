# uid-attribution-capture（A1-8 UID 归属统计操作）

> 版本：`v0.1`
> 最后更新：2026-09-23
> 责任人：成员 A
> 对应任务：A1-8
> 关联：`docs/spike-results.md`、`docs/network-core-map.md` 第 6 节、`scripts/capture-uid-attribution.*`

---

## 1. 目的

统计底座（TrackerControl/NetGuard）把网络连接归属到 UID 的成功率，用于更新 [02 能力边界表](02-android-capability-matrix.md)。

归属实现见 `docs/network-core-map.md` 第 6 节：

- 原生 `get_uid_q` → Java `ServiceSinkhole.getUidQ()` → `ConnectivityManager.getConnectionOwnerUid()`
- 只对 **TCP(6)/UDP(17)** 调用；ICMP 直接返回 `Process.INVALID_UID (-1)`，天然不可归属
- 日志 TAG：`TrackerControl.VPN`
  - `Get uid local=<saddr>:<sport> remote=<daddr>:<dport>`
  - `Get uid=<uid>`（`-1` 表示失败）

---

## 2. 前置条件

1. 手机开启「开发者选项 → USB 调试」，用数据线连接电脑并在手机上允许调试
2. 电脑安装 `adb`（Android platform-tools）：
   - Windows：下载 platform-tools，解压后把目录加入 PATH
   - 验证：`adb devices` 能看到设备
3. 手机上 TrackerControl 已开启，且正常使用过一段时间

---

## 3. 操作

### Windows（PowerShell）

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\capture-uid-attribution.ps1 -Seconds 120
```

### Linux / macOS

```bash
./scripts/capture-uid-attribution.sh 120
```

抓取期间**正常使用手机**（微信、浏览器、购物类 App 等），制造 TCP/UDP 流量。脚本结束后输出：

```text
===== A1-8 UID 归属统计 =====
getUid 调用（local 行） : ...
Get uid= 结果行          : ...
成功(uid >= 0)          : ...
失败(uid = -1)          : ...
成功率                  : ...%
```

### 手动方式（不使用脚本）

```bash
adb logcat -c
adb logcat -s TrackerControl.VPN > uid.log
# 使用手机 2~3 分钟后 Ctrl+C
grep -oE "Get uid=-?[0-9]+" uid.log | sort | uniq -c
```

---

## 4. 统计口径与限制

- 成功率 = `Get uid` 结果中 `uid >= 0` 的比例。
- 分母只含 TCP/UDP 调用（底座只对这两种协议调用 `getConnectionOwnerUid`）。
- **ICMP 不计入分母**，它固定返回 `-1`，属于设计限制而非失败。
- 工作资料 / 跨用户场景可能抛 `SecurityException`，日志中会体现为归属失败。

---

## 5. 回填

把统计结果写入 `docs/spike-results.md` 的 A1-8，并据此更新 `docs/02-android-capability-matrix.md` 的 UID 归属行。
