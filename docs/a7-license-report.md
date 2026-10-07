# A7 依赖许可证报告（自动收集 + 人工核对）

> 版本：`v0.2.0-rc1`
> 最后更新：2026-10-07
> 责任人：成员 A（生成）／成员 B（复核）
> 关联任务：[17 任务看板](17-task-board.md) A7-4；[20 开源复用建议](20-open-source-reuse-guide.md) 第 11.2/11.3 节；[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。
> 声明：自动扫描结果不是法律结论；本报告在自动收集的基础上人工核对，非 Maven 引入（submodule、数据集、构建工具）单独登记。

---

## 1. 生成方式（可复现）

```bash
scripts/generate-license-report.sh          # 输出 build/license-report/
#   raw/<module>-<configuration>.txt         Gradle 原始依赖树
#   resolved-artifacts.txt                   去重后的 group:artifact:version 清单
```

覆盖 `:app`、`:demo-app`（map 变体）、`:rule-engine`、`:core-model` 的 release 运行时类路径。工具建议见 [20 开源复用建议](20-open-source-reuse-guide.md) 第 11.3 节（Gradle-License-Report）；本 RC 以 Gradle 依赖树 + 人工核对完成，避免在发布冻结期引入新的构建插件风险。

---

## 2. 分发产物（release APK）所含第三方依赖

以下为 `:app` release 运行时类路径中的第三方组件（团队模块 `:core-model`/`:rule-engine` 除外）。全部为宽松许可证（主要为 Apache-2.0），无 GPL/AGPL 传染链路。

| 组件 / 组 | 代表 artifact（版本） | 许可证 |
|---|---|---|
| AndroidX（Compose UI / Material3 / Runtime） | `androidx.compose.ui:ui:1.12.1`、`androidx.compose.material3:material3-android:1.4.0`、`androidx.compose:compose-bom:2026.09.00` | Apache-2.0 |
| AndroidX Activity | `androidx.activity:activity-compose:1.13.0` | Apache-2.0 |
| AndroidX Navigation | `androidx.navigation:navigation-compose:2.10.2` | Apache-2.0 |
| AndroidX Lifecycle | `androidx.lifecycle:lifecycle-runtime-compose:2.11.0`、`lifecycle-viewmodel-compose:2.11.0` | Apache-2.0 |
| AndroidX Room | `androidx.room:room-runtime:2.7.0`、`room-ktx:2.7.0` | Apache-2.0 |
| AndroidX SQLite | `androidx.sqlite:sqlite:2.5.0`（封装 SQLite，SQLite 本体公有领域） | Apache-2.0 |
| AndroidX Core / Annotation / Collection / Savedstate / Window / Startup / Profileinstaller / Tracing / Emoji2 / Graphics / Versionedparcelable / Concurrent / Autofill / Customview / Interpolator / Arch.Core / NavigationEvent | 各 `androidx.*`（见 `resolved-artifacts.txt`） | Apache-2.0 |
| Kotlin 标准库 | `org.jetbrains.kotlin:kotlin-stdlib:2.2.10`（含 `-common`/`-jdk7`/`-jdk8`） | Apache-2.0 |
| kotlinx.coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0`、`kotlinx-coroutines-android:1.9.0` | Apache-2.0 |
| kotlinx.serialization | `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0`、`-core` | Apache-2.0 |
| Retrofit | `com.squareup.retrofit2:retrofit:3.0.0`、`converter-kotlinx-serialization:3.0.0` | Apache-2.0 |
| OkHttp / Okio | `com.squareup.okhttp3:okhttp:4.12.0`、`com.squareup.okio:okio:3.6.0` | Apache-2.0 |
| Guava listenablefuture（stub） | `com.google.guava:listenablefuture:1.0` | Apache-2.0 |
| JetBrains Annotations | `org.jetbrains:annotations:23.0.0` | Apache-2.0 |
| JSpecify | `org.jspecify:jspecify:1.0.0` | Apache-2.0 |

> 完整去重清单（含 demo-app/rule-engine/core-model）见脚本输出 `resolved-artifacts.txt`。

---

## 3. 仅测试依赖（不进入发布产物）

| 组件 | 版本 | 许可证 | 说明 |
|---|---|---|---|
| JUnit 4 | `junit:junit:4.13.2` | EPL-1.0 | 仅 `testImplementation`；EPL 允许测试期使用，不随 APK 分发 |
| Robolectric | `org.robolectric:robolectric:4.17` | MIT | 仅测试 |
| AndroidX Test | `androidx.test:core:1.6.1` | Apache-2.0 | 仅测试 |
| AndroidX Room Testing | `androidx.room:room-testing:2.7.0` | Apache-2.0 | 仅测试 |
| kotlinx-coroutines-test | `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0` | Apache-2.0 | 仅测试 |

---

## 4. 非 Maven 引入（必须单独登记）

| 组件 / 数据 | 版本 | 许可证 | 原创边界 / 义务 |
|---|---|---|---|
| TrackerControl Android（网络底座，submodule） | commit `9504d41b`（tag `2026080501`） | **GPL-3.0** | 随发布提供对应源码（submodule + A4-3/A5-1 补丁）；保留根 `LICENSE`；不以团队原创声称底座能力 |
| ├ A4-3 补丁 `a4-3-serversinkhole-network-hook.patch` | — | GPL-3.0 衍生 | 团队修改，登记 `THIRD_PARTY_NOTICES.md` 第 1 节 |
| └ A5-1 补丁 `a5-1-domain-block-receiver.patch` | — | GPL-3.0 衍生 | 团队修改，登记 `THIRD_PARTY_NOTICES.md` 第 1 节 |
| Disconnect Tracking Protection（tracker 域名数据） | 随底座 commit `9504d41b` 快照 | **CC BY-NC-SA 4.0** | 非商业；保留署名；派生精简数据同许可；`scripts/generate-tracker-dataset.py` |
| gotatun（底座内 Rust 依赖） | `0.8.1` | MPL-2.0 | 文件级 copyleft；未修改 |
| wgbridge-rs | 随底座 commit | GPL-3.0-only | 随底座源码提供 |

构建工具链（不随 APK 分发，但属构建依赖，登记于 `THIRD_PARTY_NOTICES.md` 第 1.1 节）：AGP `9.4.1`（Apache-2.0）、Gradle `9.6.1`（Apache-2.0）、KSP `2.3.4`（Apache-2.0）、compose-compiler-gradle-plugin / kotlin-serialization `2.2.10`（Apache-2.0）、Android SDK Platform/Build-Tools/Platform-Tools（Android SDK 条款）、NDK `27.2.12479018`、CMake `3.22.1`（BSD-3-Clause）、Rust `1.95.0` / cargo-ndk `4.1.2`（MIT OR Apache-2.0）。

---

## 5. 人工核对结论

| 检查 | 结论 |
|---|---|
| 是否存在 GPL/AGPL 传染到原创代码 | 否。GPL-3.0 仅限独立进程的网络底座；通过跨进程广播/ordered broadcast 与 App 交互，不静态链接进 `:app` |
| 是否存在强 copyleft（MPL-2.0）修改 | 有 MPL-2.0（gotatun），未修改其文件 |
| 是否存在 Unknown/无许可证 | 未发现；自动清单中所有 Maven 组件均为 Apache-2.0/EPL-1.0/MIT |
| 非商业数据 | Disconnect 数据为 CC BY-NC-SA 4.0，本项目为竞赛（非商业）用途，已署名并保持同许可 |
| GPL 对应源码可提供 | 是。submodule 固定 commit + 两补丁 + `scripts/apply-trackercontrol-hook.sh`，见 [A7 RC 记录](a7-release-candidate.md) 第 5 节 |
| 测试依赖是否误打包 | 否。JUnit/Robolectric 等仅 `testImplementation`，不进入 release 类路径 |
| 许可证报告是否替代人工登记 | 否。submodule/数据集/工具链均已在 `THIRD_PARTY_NOTICES.md` 人工登记 |

**结论**：RC 分发产物（`causalguard-0.2.0-rc1.apk`）内第三方依赖全部为宽松许可证；GPL-3.0 底座与 CC BY-NC-SA 4.0 数据已明确边界、署名与源码提供方式。人工核对未发现合规阻断项。

> 复核人与日期：成员 B / ____（待阶段 7 交叉验收完成）。
