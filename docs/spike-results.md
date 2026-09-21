# spike-results（阶段 1 技术 Spike 结果）

> 版本：`v0.1`
> 最后更新：2026-09-21
> 责任人：成员 A
> 对应任务：A1-1、A1-3、A1-4、A1-6、A1-8
> 关联：`docs/network-core-map.md`、`docs/20-open-source-reuse-guide.md`、`THIRD_PARTY_NOTICES.md`
> 状态：**未完成**（静态分析部分完成，设备构建/运行结果待 A 在真机回填）

---

## 1. 目标与止损红线

| 优先级 | 目标 | 止损时间 |
|---|---|---|
| P0 | TrackerControl 能构建、演示机启动 VPN 后可联网 | 24h |
| P0 | 能产生连接事件，或给出明确失败原因 | 48h |
| P1 | 至少一个测试域名可阻断并保留尝试记录 | 48h |
| P1 | PackageManager / Usage Access / UID 归属结论 | 48h |

失败处理：先改用固定旧 tag 重试；仍失败则收缩网络范围或转 MIT 备选路线。

---

## 2. 固定版本与环境

### 2.1 底座版本（已固定）

| 项 | 值 |
|---|---|
| 仓库 | https://github.com/TrackerControl/tracker-control-android |
| tag | `2026080501` |
| commit | `9504d41b9f6fa1509d784e5503c084d4b428307d` |
| commit 日期 | 2026-08-05 |
| 许可证 | GPL-3.0（根 `LICENSE`），部分组件/数据有独立许可证 |
| LICENSE 文件 | 随源码保留 |

### 2.2 构建链要求（来自该 commit 的静态读取，待真机确认）

| 组件 | 版本/要求 | 来源 |
|---|---|---|
| Android Gradle Plugin | 9.3.1 | `build.gradle` |
| Gradle | 9.6.1 | `gradle/wrapper/gradle-wrapper.properties` |
| compileSdk / targetSdk | 37 | `app/build.gradle` |
| minSdk | 23 | `app/build.gradle` |
| NDK | `wgbridgeNdkVersion`（`app/build.gradle` 自定义 ext） | `app/build.gradle` |
| CMake | 见 `app/build.gradle` `externalNativeBuild` | `app/build.gradle` |
| Rust（可选 WireGuard） | 1.95.0，targets: aarch64/armv7/i686/x86_64-linux-android | `rust-toolchain.toml` |
| ABI | armeabi-v7a, arm64-v8a, x86, x86_64 | `app/build.gradle` |

> 环境要求明显高于普通 App（AGP 9.x + compileSdk 37 + NDK + 可选 Rust）。这是 24h 止损的主要风险。

### 2.3 演示机与环境记录（待 A 回填）

| 项 | 值 |
|---|---|
| 构建主机 OS | _待填_ |
| JDK 版本 | _待填_ |
| Android SDK / Build Tools | _待填_ |
| NDK 版本 | _待填_ |
| 是否需要 Rust / WireGuard | _待填_ |
| 演示机型号 | _待填_ |
| Android 版本 | _待填_（基线 API 29+） |
| 构建命令 | `./gradlew assembleFdroidDebug`（待确认 flavor） |
| 构建结果 | _待填（成功/失败 + 报错）_ |
| APK 路径 | _待填_ |

> flavor：`play` / `fdroid` 两个 dimension（`app/build.gradle`）。演示建议用 `fdroid` 避免 Play 依赖。

---

## 3. 验证结果

### A1-1 VPN 启动后可联网

- [ ] 待验证
- 结果：_待填_
- 证据：_截图/日志路径_

### A1-3 PackageManager（包名/权限）

- [ ] 待验证
- 输出字段：App、UID、版本、声明权限、授权状态
- 结果：_待填_

### A1-4 Usage Access（前后台）

- [ ] 待验证
- 结果：_待填_（能读到前后台 / 明确 unknown 或失败原因）

### A1-5 最小 NetworkEvent

- [ ] 待验证
- 结果：_待填_（脱敏 JSON 示例）
- 注意：`Packet` 不含字节数，见 `docs/network-core-map.md` 第 8 节差异项。

### A1-6 最小阻断验证

- [ ] 待验证
- 测试域名：_待填_
- 结果：_待填_（是否阻断成功、是否保留尝试记录）

### A1-8 UID 归属成功率

- [ ] 待验证
- 建议按协议统计：

| 协议 | 成功 | 失败/unknown | 成功率 |
|---|---|---|---|
| TCP | _待填_ | _待填_ | _待填_ |
| UDP | _待填_ | _待填_ | _待填_ |
| ICMP | 不适用 | _待填_ | - |

---

## 4. 决策记录

| 决策 | 结论 | 日期 |
|---|---|---|
| 网络底座 | 方案 A：TrackerControl（GPL-3.0），tag `2026080501` | 2026-09-21 |
| 是否修改 native | 否，保留原生核心 | 2026-09-21 |
| 适配层侵入方式 | 倾向方案 1：在 `ServiceSinkhole` 回调追加适配（待确认） | 2026-09-21 |

---

## 5. 未决/阻塞

1. 本仓库开发环境无 JDK / Android SDK / Gradle / NDK，**构建必须在 A 的构建机完成**。
2. `bytesIn/bytesOut` 数据源与契约冲突（`docs/09` vs `Packet`）。
3. 包目录命名 `vpn/` vs `network/` 需统一。
4. 第三方源码是否在本仓库单独 commit 导入，待决策。
