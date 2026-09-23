# spike-results（阶段 1 技术 Spike 结果）

> 版本：`v0.1`
> 最后更新：2026-09-23
> 责任人：成员 A
> 对应任务：A1-1、A1-3、A1-4、A1-6、A1-8
> 关联：`docs/network-core-map.md`、`docs/20-open-source-reuse-guide.md`、`THIRD_PARTY_NOTICES.md`
> 状态：**进行中**（A1-1 构建+真机联网、A1-5 真实事件捕获、A1-6 真机阻断均通过；A1-3/A1-4/A1-8 待补）

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

### 2.3 演示机与环境记录（构建部分已完成）

| 项 | 值 |
|---|---|
| 构建主机 OS | Ubuntu 22.04.5 LTS (x86_64) |
| JDK | Temurin 17.0.13+11 |
| Android SDK / Build Tools | platform `android-37.0`、build-tools `37.0.0`、platform-tools `37.0.1` |
| CMake | 3.22.1（SDK 包） |
| NDK | 27.2.12479018 |
| Rust / WireGuard | rustup 1.29.1 + Rust 1.95.0（4 个 Android target）+ cargo-ndk 4.1.2；APK 打包必须构建 `libwgbridge.so` |
| 演示机型号 | 真机 _（型号待补）_ |
| Android 版本 | _待补（基线 API 29+）_ |
| 构建命令 | `gradle --no-daemon assembleFdroidDebug`（Gradle 9.6.1；wrapper 分发地址被网络策略拦截，改用系统安装的 9.6.1） |
| 构建结果 | **成功**，BUILD SUCCESSFUL in 9m 6s（首次）/ 3m 10s（缓存命中） |
| APK 路径 | 构建产物 `app/build/outputs/apk/fdroid/debug/TrackerControl-fdroidDebug-latest.apk`；已复制持久副本到构建机 `~/trackercontrol-apk/` |
| APK 大小 / SHA-256 | 24,711,138 bytes / `ed2b6d7b80bb7ff0b2d623686ce9c09708af5f3f4cf83545f9908806f8d222c8`（两次构建哈希一致，可复现） |

> 网络受限环境下的镜像替换（仅本构建机，不改本仓库）：Adoptium/rustup/static.crates.io/services.gradle.org/repo1.maven.org 不可达。
> 改用：JDK 从 GitHub Releases；rustup 与 crates 用 `rsproxy.cn`（`RUSTUP_DIST_SERVER=https://rsproxy.cn`，cargo sparse 源替换）；Gradle 发行包用腾讯镜像（`mirrors.cloud.tencent.com/gradle`）；Maven Central 用阿里云镜像（Gradle init script 注入），google() 可直连。

---

## 3. 验证结果

### A1-1 VPN 启动后可联网

- [x] 固定版本构建成功（本构建机）
- [x] APK 安装到真机
- [x] VPN 启动后可联网（真机验证通过，2026-09-23）
- 结果：TrackerControl 总开关开启、授权 VPN 后，真机正常联网；App 成功捕获微信、企业微信、畅课、支付宝等真实网络请求并放行，说明 VPN 已工作且未阻断网络。
- 安装方式：真机浏览器从临时分享链接下载 APK 后安装（debug 签名，允许未知来源）；构建机持久副本 `~/trackercontrol-apk/TrackerControl-fdroidDebug-latest.apk`
- 证据：真机“时间轴”页面捕获记录（见 A1-5）；构建日志 BUILD SUCCESSFUL

### A1-3 PackageManager（包名/权限）

- [ ] 待验证
- 输出字段：App、UID、版本、声明权限、授权状态
- 结果：_待填_

### A1-4 Usage Access（前后台）

- [ ] 待验证
- 结果：_待填_（能读到前后台 / 明确 unknown 或失败原因）

### A1-5 最小 NetworkEvent

- [x] 已捕获真实连接事件（初步证据，2026-09-23）
- 真机观察（屏蔽模式 = Minimal）：
  - 最近 7 天：联系跟踪主机 **34 → 66**，涉及跟踪公司 **2 → 8**，已屏蔽比例 **0% → 32%**
  - 时间轴样本：微信、企业微信、小红书、畅课 → `Tencent · Content` / `Alibaba · Content`（放行）；鲨鱼记账 → `Baidu Analytics`/`ByteDance`/`Alibaba`；京东 → `JD.com`；夸克 → `Alibaba`/`AlibabaGroup Fingerprinting`/`Cloudflare Analytics`；支付宝 → `Alibaba`
- 结论：底座能产出“时间、App、域名/公司、类目、放行/屏蔽”级别的连接事件，可作为 `NetworkEvent` 数据源。
- 待补：导出脱敏 JSON 样例（时间、协议、IP/域名线索、端口、UID/unknown）。
- 注意：`Packet` 不含字节数，见 `docs/network-core-map.md` 第 8 节差异项。

### A1-6 最小阻断验证

- [x] 至少在真机上成功阻断并保留尝试记录（2026-09-23）
- 真机观察（屏蔽模式 = Minimal）：
  - 最近 7 天：跟踪主机 **34 → 66**、跟踪公司 **2 → 8**、已屏蔽比例 **0% → 32%**
  - 被拦截样本：
    - 鲨鱼记账：3 个被拦 / 1 个放行，涉及 `Baidu Analytics`、`ByteDance`、`Alibaba`
    - 京东：`JD.com` 被拦
    - 夸克：`Alibaba`、`AlibabaGroup Fingerprinting`、`Cloudflare Analytics` 多个被拦
    - 支付宝：`Alibaba` 被拦
  - 仍放行（`Content` 类目，Minimal 设计不拦）：微信/小红书/企业微信/畅课的 `Tencent`、`Alibaba Content`
- 结论：底座具备“域名→类目→拦截并保留尝试记录”的能力，满足 A1-6。
- 证据：真机“时间轴”页面截图（A 持有）。

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
| 适配层侵入方式 | 方案 1：在 `ServiceSinkhole` 回调追加适配 | 2026-09-21 |
| 包目录命名 | 统一用 `network/`（已同步 `docs/19`，事件类型为 `network`） | 2026-09-21 |
| `bytesIn/bytesOut` | P0 置空/0，`docs/09` 已加标注，P1 再补 | 2026-09-21 |
| 第三方源码 | 外部保留，仅登记固定 commit，不导入本仓库 | 2026-09-21 |
| 构建环境 | A 本机构建，步骤见 `docs/spike-build-guide.md` | 2026-09-21 |

---

## 5. 未决/阻塞

1. A1-1（构建 + 真机联网）、A1-6（真机阻断 32%）已完成，见 2.3 与第 3 节。
2. 待补设备数据：演示机型号/Android 版本、A1-3、A1-4、A1-8（UID 归属成功率）。
3. 待导出 A1-5 的脱敏 JSON 样例。
