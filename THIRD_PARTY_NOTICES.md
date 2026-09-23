# THIRD_PARTY_NOTICES 第三方开源与数据来源登记

> 版本：`v0.1`
> 最后更新：2026-09-21
> 责任人：成员 A（生成）／成员 B（汇总与复核）
> 目的：登记所有引入本仓库的第三方源码、二进制、数据与素材的来源、版本、许可证、使用范围和团队修改，保证竞赛提交与发布时许可证合规、原创边界清晰。

## 0. 使用规则

1. **先固定版本再引入**：所有第三方组件必须固定 tag 或完整 commit SHA，禁止长期跟随浮动 `main`。
2. **引入前核对**：读取具体 commit/tag 内的根 `LICENSE`、`NOTICE` 以及被复制文件的头部声明；如与本表记录不一致，以该版本文件为准并更新本表。
3. **分离提交**：第三方源码首次导入单独一个 commit；团队修改另起 commit，并在本表登记实际修改文件。
4. **区分代码与数据**：代码许可证与数据许可证分开登记，不混写。
5. **自动工具不替代人工**：AboutLibraries、Gradle License Report 等自动扫描结果必须人工复核，手工复制源码与数据集需自行登记。
6. **不夸大能力**：答辩与文档不得把第三方网络底座声称为团队原创。

许可证等级与“能否挪用”的判定见 [docs/20-open-source-reuse-guide.md](docs/20-open-source-reuse-guide.md) 第 2 节。本文不是法律意见，最终以具体版本内的许可证文件为准。

---

## 1. 已导入的第三方组件

> 当前仓库尚未导入任何第三方源码。每次实际导入后，复制下面的模板追加条目。

<!-- 模板：
## <组件名称>

- Repository: <URL>
- Commit/Tag: <固定完整 SHA 或 tag>
- License: <SPDX 标识>
- Used files/modules: <实际使用范围>
- Local modifications: <修改说明>
- Purpose: <用途>
- Included license file: YES / NO（放在何处）
- Source availability: <对应源码目录或发布方式>
- Team-original boundary: <团队原创边界>
- Registered by / date: <成员 / 日期>
-->

无。

### 1.1 构建与运行时技术依赖（阶段 1 / A1-7 登记）

> 这些是构建底座 APK 时实际使用的工具链与运行时依赖；许可证以各版本内 `LICENSE`/`NOTICE` 为准。

| 组件 | 版本 | 许可证 | 用途 |
|---|---|---|---|
| Android Gradle Plugin | 9.4.1（底座用 9.3.1） | Apache-2.0 | Android 构建 |
| Gradle | 9.6.1 | Apache-2.0 | 构建系统 |
| Kotlin（AGP 9 内置） | 随 AGP | Apache-2.0 | Kotlin 编译 |
| Android SDK Platform | android-37.0 | Android SDK 条款 | 编译目标 |
| Android SDK Build-Tools | 37.0.0 | Android SDK 条款 | 打包 |
| Android SDK Platform-Tools | 37.0.1 | Apache-2.0（含 adb） | 设备工具 |
| Android NDK | 27.2.12479018 | 见 NDK 内 NOTICE（含 LLVM/clang，Apache-2.0 with LLVM exceptions 等） | JNI/CMake 原生编译 |
| CMake | 3.22.1 | BSD-3-Clause | 原生构建 |
| Rust 工具链 | 1.95.0 | MIT OR Apache-2.0 | 编译 WireGuard 桥 |
| cargo-ndk | 4.1.2 | MIT OR Apache-2.0 | Rust 交叉编译到 Android |
| wgbridge-rs（TrackerControl 内） | 随底座 commit | GPL-3.0-only | WireGuard 桥接库 |
| gotatun | 0.8.1 | MPL-2.0 | WireGuard 协议实现（Rust） |
| tokio / base64 / hex / ipnetwork / getrandom / libc / log / jni / android_logger | 见 `wgbridge-rs/Cargo.lock` | MIT / Apache-2.0（各 crate 为准） | Rust 运行时依赖 |

- TrackerControl 内部集成的 tracker/域名数据管线随底座引入，其数据来源与许可证以底座对应 commit 内的说明为准（另见 `docs/20-open-source-reuse-guide.md` 第 7 节）。
- 数据依赖若要单独精简或替换，按 `docs/20` 第 7.3 节单独登记数据集。

---

## 2. 已导入的第三方数据集

<!-- 模板：
## <数据集名称>

- Source: <URL>
- Version/date: <版本或下载日期>
- Data license: <许可证>
- Transform script: <脚本位置>
- Fields retained: <字段>
- Attribution shown in app: YES / NO
- Commercial-use restriction: <有/无>
- Registered by / date: <成员 / 日期>
-->

无。

---

## 3. 候选组件清单（尚未导入，仅登记候选）

> 以下为已调研的候选，**在固定版本并完成第 0 节流程前不得将其代码或数据直接复制进仓库**。

| 组件 | 仓库 | 许可证（需按具体版本复核） | 计划用途 | 主责 |
|---|---|---|---|---|
| TrackerControl Android | https://github.com/TrackerControl/tracker-control-android | GPL-3.0（部分第三方组件/数据另有许可） | VPN/TUN、DNS、连接记录、域名阻断底座 | 成员 A |
| ↳ 已固定版本 | tag `2026080501`，commit `9504d41b9f6fa1509d784e5503c084d4b428307d`（2026-08-05），根 `LICENSE` 为 GPL-3.0 | 构建链：AGP 9.3.1 / Gradle 9.6.1 / compileSdk 37 / NDK / 可选 Rust 1.95.0 | 阶段 1 静态分析已完成，见 `docs/network-core-map.md`；源码尚未导入本仓库 | 成员 A |
| NetGuard | https://github.com/M66B/NetGuard | GPL-3.0 | TrackerControl 上游参考；本地网络转发研究 | 成员 A |
| android/architecture-templates | https://github.com/android/architecture-templates | Apache-2.0 | 工程分层骨架参考 | 成员 A |
| android/architecture-samples | https://github.com/android/architecture-samples | Apache-2.0 | Repository/ViewModel/UI State 参考 | 成员 A |
| android/platform-samples | https://github.com/android/platform-samples | Apache-2.0 | 平台 API 与权限用法参考 | 成员 A |
| kotlinx.serialization | https://github.com/Kotlin/kotlinx.serialization | Apache-2.0 | 事件/规则/评测 JSON 序列化 | 成员 A |
| AndroidX Room | https://developer.android.com/jetpack/androidx/releases/room | Apache-2.0 | 本地数据库 | 成员 A |
| Jetpack Compose samples | https://github.com/android/compose-samples | Apache-2.0 | UI 结构参考 | 成员 B |
| Retrofit + OkHttp | https://github.com/square/retrofit / https://github.com/square/okhttp | Apache-2.0 | 在线 AI 解释（P1） | 成员 A 配置 / 成员 B 实现 |
| AndroidX Test + Turbine | https://github.com/android/android-test / https://github.com/cashapp/turbine | Apache-2.0 | 单元与 Flow 测试 | 各自模块 |
| detekt | https://github.com/detekt/detekt | Apache-2.0 | Kotlin 静态检查 | 成员 A |
| AboutLibraries | https://github.com/mikepenz/AboutLibraries | Apache-2.0 | App 内开源许可页 | 成员 A |
| Gradle-License-Report | https://github.com/jk1/Gradle-License-Report | Apache-2.0 | 提交前许可证审计 | 成员 A |
| Public Suffix List | https://github.com/publicsuffix/list | MPL-2.0 | 域名归一化数据 | 成员 A |
| DuckDuckGo Tracker Radar / Blocklists | https://github.com/duckduckgo/tracker-radar | CC BY-NC-SA 4.0 | tracker 分类/阻断数据（二选一） | 成员 B |
| Disconnect Tracking Protection | https://github.com/disconnectme/disconnect-tracking-protection | CC BY-NC-SA 4.0 | tracker 分类数据备选 | 成员 B |

> 禁止项（P0 明确不引入）：TLS MITM 项目、Exodus Core（AGPL-3.0）、完整 DuckDuckGo App、本地大模型运行时、大型图数据库/规则 DSL。详见 [docs/20-open-source-reuse-guide.md](docs/20-open-source-reuse-guide.md) 第 12 节。

---

## 4. 团队原创边界（声明）

以下为本项目团队原创，非第三方能力：

- `PrivacyEvent` 统一事件模型与事件适配层；
- UsageStats 与网络行为的时间关联；
- 场景知识库与场景一致性判断；
- 证据等级（E1–E5）与证据链构建；
- 风险规则内容、阈值与因果解释；
- 处置建议与处置后复查算法；
- Ground Truth Demo 场景；
- 产品 UI、解释文案与评测数据集。

第三方网络底座仅解决“网络事实采集与阻断”，上述“翻译、推理、解释、复查”价值由团队实现。
