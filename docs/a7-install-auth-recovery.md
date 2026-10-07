# A7 安装·授权·清理·故障恢复

> 版本：`v0.2.0-rc1`
> 最后更新：2026-10-07
> 责任人：成员 A（协作：成员 B）
> 关联任务：[17 任务看板](17-task-board.md) A7-3；[03 权限与授权流程](03-permission-and-consent-flow.md)；[16 演示与发布方案](16-demo-and-release-plan.md) 第 1~2 节；[spike-build-guide](spike-build-guide.md)。
> 目标：成员 B 不看口头说明即可独立完成安装、授权、演示、重置与故障恢复（[21 并行分工与协作规范](21-parallel-work-allocation-plan.md) 第 11 节交叉验收）。

本文件面向**演示机 PJW110 / Android 16（API 36）**的现场操作；命令示例默认在 WSL2 中执行，`adb` 经无线调试连接。

---

## 1. 交付物清单

| 文件 | 说明 | 来源 |
|---|---|---|
| `causalguard-0.2.0-rc1.apk` | 主 App `com.causalguard`（versionName `0.2.0-rc1`） | `scripts/build-release-rc.sh`，见 [A7 RC 记录](a7-release-candidate.md) |
| TrackerControl 底座 APK | `net.kollnig.missioncontrol.fdroid.test`（固定 commit `9504d41b` + A4-3/A5-1 补丁） | `third_party/tracker-control-android` + `scripts/apply-trackercontrol-hook.sh` |
| Demo App APK | `com.demo.map` / `com.demo.calculator` / `com.demo.weather` | `demo-app` 模块，见下方构建命令 |
| 文档 | 本文件、[A7 RC 记录](a7-release-candidate.md)、[A7 许可证报告](a7-license-report.md) | 本仓库 |

构建 Demo App（可选，逐场景）：

```bash
./gradlew :demo-app:assembleMapRelease     # com.demo.map
./gradlew :demo-app:assembleCalculatorRelease  # com.demo.calculator
./gradlew :demo-app:assembleWeatherRelease     # com.demo.weather
```

> 真机验证时曾使用 debug 变体；RC 演示建议使用对应的 release 变体，并统一签名与版本（见 [A7 RC 记录](a7-release-candidate.md)）。

---

## 2. 前置条件

1. Android SDK Platform-Tools（含 `adb`）；
2. 演示机已开启开发者选项与**无线调试**（Android 11+）；
3. 电脑与手机在同一网段；
4. 已构建底座 APK（含补丁）与主 App RC APK。

WSL2 默认不把 USB 设备透传进 Linux，优先使用无线调试：

```bash
adb pair <手机IP>:<配对端口>       # 输入手机显示的 6 位配对码
adb connect <手机IP>:<调试端口>     # 无线调试主界面显示的“IP 地址和端口”
adb devices
```

USB 直连需在 Windows 侧用 `usbipd-win` 透传后再在 WSL `adb devices`。

---

## 3. 安装步骤（顺序：底座 → 主 App → Demo App）

```bash
# 1) 网络底座（含 A4-3/A5-1 补丁；如需重建见第 6 节）
adb install -r ~/a4-3-apks/trackercontrol-fdroidDebug-PATCHED.apk

# 2) 主 App RC
adb install -r ~/causalguard-release/0.2.0-rc1/causalguard-0.2.0-rc1.apk

# 3) Demo App（按演示需要安装对应场景）
adb install -r demo-app/build/outputs/apk/map/release/demo-app-map-release-unsigned.apk
adb install -r demo-app/build/outputs/apk/calculator/release/demo-app-calculator-release-unsigned.apk
adb install -r demo-app/build/outputs/apk/weather/release/demo-app-weather-release-unsigned.apk
```

> 底座与主 App 必须**同签名**，否则 A5-1 signature 权限控制通道不生效（见 [A7 RC 记录](a7-release-candidate.md) 与 `docs/spike-results.md` §3 A5-1）。底座 debug 签名与 RC release 签名不同，现场做域名阻断演示时需使用与底座同签名的 CausalGuard 构建（两个 APK 用同一 keystore 签名）。

---

## 4. 授权步骤（一次一项，先说明后申请）

1. 打开主 App → 隐私说明页（本地优先、不采集原始内容、可随时停止）；
2. **通知权限**：允许，用于实时告警；
3. **使用情况访问**：跳转 系统设置 → 特殊应用权限 → 使用情况访问 → 允许（返回后 App 自动检测状态）；
4. **VPN 连接请求**：系统弹窗 → 允许；
5. 安装并启动 Demo App，选择演示场景。

每项授权后可在首页看到状态：已授权 / 未授权 / 被撤销。拒绝任一项不闪退：通知缺失仅应用内告警；Usage Access 缺失则风险结论降级；VPN 缺失则进入演示/离线模式。

---

## 5. 演示前检查与场景重置

| 检查 | 命令 / 操作 | 期望 |
|---|---|---|
| 设备在线 | `adb devices` | 设备状态为 `device` |
| 底座 VPN | `adb shell ip addr \| grep tun0` | 出现 `tun0` |
| 主 App 版本 | `adb shell dumpsys package com.causalguard \| grep versionName` | `0.2.0-rc1` |
| 监测状态 | App 内「Start Network Monitor」 | `active=true collecting=true` |
| Demo 场景重置 | 对应 Demo App 内的 Reset 按钮 | baseline/session 清除，可重新 Arm |

Demo App 场景复位：在 Demo App 内点击 **Reset** 清除该场景状态（Demo Weather 的 `SharedPreferences` baseline session、Demo Calculator 探针状态等）。CausalGuard 本地数据清理见第 7 节。

---

## 6. 重建网络底座（含 A4-3/A5-1 补丁）

```bash
git submodule update --init --recursive
scripts/apply-trackercontrol-hook.sh          # 应用两补丁（--revert 回滚）
cd third_party/tracker-control-android
./gradlew assembleFdroidDebug                   # 产出 fdroid debug 底座
```

> 底座构建需 NDK/CMake（及可选 Rust）。补丁 SHA-256 与证据见 [A7 RC 记录](a7-release-candidate.md) 第 5 节。

---

## 7. 清理与数据控制

### 7.1 App 内

- **暂停监测**：停止 VPN 采集与 Usage 轮询，保留历史数据；
- **删除本地数据**：清空事件库、画像、处置记录，二次确认。

### 7.2 卸载与彻底清理

```bash
# 停止采集并撤销 VPN（先停止监测再卸载，避免残留隧道路由）
adb shell am force-stop com.causalguard

# 清除 App 数据（事件库、SharedPreferences）
adb shell pm clear com.causalguard

# 卸载主 App、底座与 Demo App
adb uninstall com.causalguard
adb uninstall net.kollnig.missioncontrol.fdroid.test
adb uninstall com.demo.map
adb uninstall com.demo.calculator
adb uninstall com.demo.weather
```

> 清理演示用真实数据：`docs/fixtures/real-network-events-a4-v0.1*.json` 为脱敏离线 fixture；原始设备数据库/日志不得入库（[12 隐私与安全设计](12-privacy-security-design.md)、`THIRD_PARTY_NOTICES.md` 第 0 节）。

---

## 8. 故障恢复

| 故障 | 现象 | 恢复步骤 |
|---|---|---|
| VPN 被回收 | “监测已暂停”，无新事件 | 前台服务 `START_STICKY` 尝试恢复；失败则 App 内手动重启监测，或 `adb shell am start` 重新打开 App |
| 拒绝/撤销 VPN | 无法真实观测 | 进入演示/离线模式；重新授权：设置 → 应用 → CausalGuard → 允许 VPN，或重新触发系统连接请求 |
| 拒绝 Usage Access | 缺少前后台上下文，结论降级 | 重新跳转 设置 → 特殊应用权限 → 使用情况访问 授权，返回后自动检测 |
| 通知被关闭 | 无实时通知 | 设置中重新允许通知；不影响 App 内告警 |
| 断网 | 公网 AI 不可用 | 本地规则/模板照常运行，主演示不依赖公网 |
| UID 无法归属 | 事件显示 unknown | 诚实降级，不强行归因；见 `docs/02`、[18 风险清单](18-risk-register.md) RK-2 |
| 域名不可见 | 只见 IP | 展示 IP + 类别降级，不强行生成域名结论（RK-3） |
| 端口/无线调试断开 | `adb devices` 为空 | 重新 `adb connect <IP>:<端口>`；必要时重新 `adb pair` |
| 底座与 App 签名不一致 | 域名阻断不生效（回执 `RESULT_CANCELED`） | 用同一 keystore 重新签名两个 APK 后重装 |
| 数据异常/需要干净环境 | 事件重复或旧数据 | `adb shell pm clear com.causalguard` 后重新启动 |

---

## 9. 卸载后验收

- [ ] `adb shell dumpsys activity services com.causalguard` 无残留 ServiceRecord；
- [ ] `adb shell ip addr` 无残留 `tun0`；
- [ ] 三个 Demo App 与底座均已卸载；
- [ ] 本地事件库已清除，敏感内容不可从 App 恢复。
