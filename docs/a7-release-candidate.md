# A7 发布候选（Release Candidate）记录

> 版本：`v0.2.0-rc1`
> 最后更新：2026-10-07
> 责任人：成员 A
> 关联任务：[17 任务看板](17-task-board.md) A7-1～A7-6；[16 演示与发布方案](16-demo-and-release-plan.md)；[21 并行分工与协作规范](21-parallel-work-allocation-plan.md) 第 11 节。
> 配套文档：[A7 安装·授权·清理·故障恢复](a7-install-auth-recovery.md)、[A7 依赖许可证报告](a7-license-report.md)。

本文件冻结阶段 7 A 侧的发布候选基线：设备与构建环境、RC 构建与签名记录、真实数据与第三方修改证据、阻断性缺陷排查结论。

---

## 1. A7-1 冻结设备、系统与构建环境

### 1.1 演示设备（冻结，不再中途更换）

| 项 | 值 |
|---|---|
| 品牌/型号 | PJW110 |
| Android 版本 | Android 16 |
| API Level | 36 |
| 网络底座 | `net.kollnig.missioncontrol.fdroid.test`（TrackerControl fdroid debug，tag `2026080501` / commit `9504d41b`） |
| App | `com.causalguard`（versionCode 2 / versionName `0.2.0-rc1`） |
| 授权 | 通知、Usage Access、VPN 三项在演示前完成授权（见 [安装·授权文档](a7-install-auth-recovery.md)） |

> 设备差异风险见 [18 风险清单](18-risk-register.md) RK-5；首版基线为 Android 10 / API 29+，本 RC 的目标设备为 Android 16 / API 36。

### 1.2 构建环境（冻结）

| 组件 | 版本 | 说明 |
|---|---|---|
| 构建宿主 | Windows 11 + WSL2（`Linux 5.15.167.4-microsoft-standard-WSL2`，amd64） | 本地构建 |
| JDK | Eclipse Adoptium Temurin `17.0.13+11` | `JAVA_HOME=$HOME/tools/jdk-17.0.13+11` |
| Gradle | `9.6.1` | wrapper 固定（`gradle-wrapper.properties`） |
| Android Gradle Plugin | `9.4.1` | `build.gradle`（root） |
| Kotlin | AGP 9 内置（stdlib `2.2.10`） | 不引入独立 KGP（见 RK-21） |
| compose-compiler-gradle-plugin | `2.2.10` | root `build.gradle` |
| kotlin-serialization 插件 | `2.2.10` | root `build.gradle` |
| KSP | `2.3.4` | Room 注解处理 |
| compileSdk / targetSdk | `37` | `app/build.gradle` |
| minSdk | `29` | Android 10 基线 |
| Android SDK Platform | `android-37.0` | `$HOME/android-sdk/platforms` |
| Android SDK Build-Tools | `37.0.0` | 含 `apksigner` / `zipalign` |
| Android SDK Platform-Tools | `37.0.1` | 含 `adb` |
| Android NDK | `27.2.12479018` | 仅底座（TrackerControl）原生构建 |
| CMake | `3.22.1` | 仅底座原生构建 |
| Rust / cargo-ndk | `1.95.0` / `4.1.2` | 仅底座 WireGuard 桥构建 |
| 网络底座 submodule | `third_party/tracker-control-android` commit `9504d41b9f6fa1509d784e5503c084d4b428307d`（tag `2026080501`） | 克隆需 `git submodule update --init --recursive`（RK-22） |
| 关键库版本 | Room `2.7.0`、Compose BOM `2026.09.00`、Retrofit/OkHttp `3.0.0`/`4.12.0` | 见 [依赖许可证报告](a7-license-report.md) |

环境变量脚本：`$HOME/tools/android-env.sh`（设置 `JAVA_HOME` / `ANDROID_HOME` / `PATH`）。

---

## 2. A7-2 RC 构建与签名记录

| 项 | 值 |
|---|---|
| 应用 | `com.causalguard` |
| versionName / versionCode | `0.2.0-rc1` / `2` |
| 构建类型 | `release`（`minifyEnabled false`；RC 阶段不启用混淆，避免引入非阻断风险） |
| 源码构建 commit | `c603d26be0f657d8267434dae13c0ceea180f026`（APK 生成于该 commit；tag `causalguard-v0.2.0-rc1` 指向记录本表的发布提交） |
| 构建命令 | `scripts/build-release-rc.sh`（内部执行 `:app:assembleRelease` → `zipalign` → `apksigner sign`） |
| APK 文件名 | `causalguard-0.2.0-rc1.apk` |
| APK SHA-256 | `f1df27fb1d5a17a9432a1a417f23cb604dd8e283980c9474499e8c0e9778f3d7` |
| 未签名 APK SHA-256 | `6120d53aa2b8a790d1a32621c7ef39695cf0c34dec457a34b416325704f1b376` |
| 构建内嵌 VCS 信息 | `META-INF/version-control-info.textproto` 记录 `revision=c603d26…`（AGP 默认行为）。因此在**其它 commit 上重建会得到不同 APK 哈希**；复现本 RC 请 checkout 源码构建 commit `c603d26` |
| 签名方案 | APK Signature Scheme v3（`apksigner 0.9` 对 minSdk 29 仅写 v3；Android 10+ 支持 v3） |
| 签名证书 DN | `CN=CausalGuard, OU=Privacy Causal Sentinel, O=CausalGuard, L=Beijing, ST=Beijing, C=CN` |
| 签名证书 SHA-256 | `b3ed3f7d7d80a3c4c6276792f91dd76b968138945928bfe62cfe17f3673554ef` |
| 公钥算法 | RSA 4096 |
| keystore | `$HOME/causalguard-release/causalguard-release.jks`（**不入库**，`.gitignore` 覆盖 `*.jks`/`*.keystore`/`keystore.properties`） |
| 构建可复现性 | 同一 commit 清空 `:app:clean` 重建，未签名 APK SHA-256 一致（确定性构建） |

> 构建产物不入库（`.gitignore` 排除 `*.apk`/`*.aab`）；APK 与 keystore 保存在构建机 `$HOME/causalguard-release/`，随发布归档。

---

## 3. A7-3 安装、授权、清理与故障恢复

见独立文档 [A7 安装·授权·清理·故障恢复](a7-install-auth-recovery.md)。

---

## 4. A7-4 依赖许可证报告

见独立文档 [A7 依赖许可证报告](a7-license-report.md)（含自动收集命令与人工核对结论）。

---

## 5. A7-5 真实网络数据来源、日志与第三方修改证据

### 5.1 真实网络数据来源

| 项 | 值 |
|---|---|
| 采集底座 | TrackerControl Android（GPL-3.0），submodule `third_party/tracker-control-android`，commit `9504d41b9f6fa1509d784e5503c084d4b428307d` |
| 采集方式 | 底座 `ServiceSinkhole` 回调发送跨进程显式包名广播；App 侧 `TrackerControlEventSource` → `NetworkEventIngestor` → Room（`docs/09` 唯一写入口） |
| 真机证据 | [spike-results](spike-results.md) §3 A4-3（486 条事件一一对应入库）、A4-5（VPN 回收/恢复事件不丢）、A6-3（稳定性） |
| UID 归属量化 | 231/231 = 100%（[uid-attribution-capture](uid-attribution-capture.md)、[spike-results](spike-results.md) §3 A4-4） |
| 域名分类数据 | Disconnect Tracking Protection（CC BY-NC-SA 4.0），快照见 `THIRD_PARTY_NOTICES.md` 第 2 节 |

### 5.2 第三方修改证据（GPL 对应源码）

底座不 fork、以固定 commit 的 submodule 引入；团队修改以补丁交付，应用脚本 `scripts/apply-trackercontrol-hook.sh`（支持 `--revert`）。

| 补丁 | 用途 | SHA-256 |
|---|---|---|
| `third_party/patches/a4-3-serversinkhole-network-hook.patch` | A4-3 网络事件广播桥接（新增 `CausalGuardNetworkHook`，在 `ServiceSinkhole` 两处回调挂接） | `97dec53c117a36da615672068e5aacc6e31570200d442bce02046bd463fbe024` |
| `third_party/patches/a5-1-domain-block-receiver.patch` | A5-1 域名阻断控制通道（`CausalGuardDomainBlockReceiver` + `mapCausalGuardBlocked` + `isDomainBlocked` DNS 抑制 + signature 权限） | `28b7ef52d68ae25c2a5496d839fbe139e0e607c21cc45b5c60d58d22e329d3ad` |
| `scripts/apply-trackercontrol-hook.sh` | 应用/回滚上述补丁 | `6d259922f6d072e4786983c419192a8b1b15d7ffd64ea6b94f77c3ab068da5b3` |

复现第三方修改证据：

```bash
git submodule update --init --recursive
cd third_party/tracker-control-android
git apply --check ../patches/a4-3-serversinkhole-network-hook.patch
git apply --check ../patches/a5-1-domain-block-receiver.patch
```

> 应用补丁后的底座源码即“对应源码”，随发布提供；登记见 `THIRD_PARTY_NOTICES.md` 第 1 节与 [20 开源复用建议](20-open-source-reuse-guide.md) 第 7 节。

---

## 6. A7-6 阻断性缺陷排查

阶段 7 只修阻断性缺陷，不新增功能（[16 演示与发布方案](16-demo-and-release-plan.md) 第 6 节）。

| 检查 | 命令 | 结果（2026-10-07 本地） |
|---|---|---|
| release 构建 | `./gradlew :app:assembleRelease` | `BUILD SUCCESSFUL`（可确定性重建） |
| 单元测试 | `./gradlew test` | **261 通过 / 0 失败 / 0 错误**（`:app` 115、`demo-app` 108、`:rule-engine` 31、`:core-model` 7） |
| lint | `./gradlew :app:lintDebug` | **0 error / 4 warning** |

4 条 lint warning 均为非阻断，按“只修阻断性缺陷”不在 RC 阶段处理：

| warning | 位置 | 处理 |
|---|---|---|
| 可升级依赖（okhttp 4.12.0、kotlinx-coroutines-test 1.9.0） | `app/build.gradle` | 保持固定版本，升级进入 backlog |
| `allowBackup` deprecated（建议 `dataExtractionRules`） | `AndroidManifest.xml:29` | 非阻断；当前 `allowBackup=false`，无外泄风险 |
| 未显式设置 `android:icon` | `AndroidManifest.xml:28` | 非阻断，但影响观感；登记为发布前 backlog 项 |

**结论**：RC 无阻断性缺陷，无需为 A7-6 修改代码。`android:icon` 缺失作为已知非阻断项登记，交由后续（A8/B 侧视觉）处理。

---

## 7. 已知限制与降级（不夸大能力）

- `BLOCK_DOMAIN` 为 P0 保证能力；`BLOCK_APP` 明确 `UNSUPPORTED`（待 P1），绝不谎报。
- 未接线/未构建底座时，处置按 `UNAVAILABLE` 诚实降级。
- 长时间/深度 Doze 下的保活属遗留项（A4-5、A6-3 已登记）。
- 在线 AI 为可选增强，密钥未注入时整体不可用并回退本地模板；RC 不保证公网 AI 连通。
- tracker 命中率受数据覆盖与采样影响（B4-4 diagnosis 为 mixed），不将“未命中”表述为“安全”。

---

## 8. 复现步骤

```bash
git switch main && git pull --ff-only origin main
git switch <RC tag/commit>
git submodule update --init --recursive
$HOME/tools/android-env.sh           # 或手动 export JAVA_HOME/ANDROID_HOME
./gradlew :app:assembleRelease
scripts/build-release-rc.sh          # 需 keystore.properties
sha256sum $HOME/causalguard-release/0.2.0-rc1/causalguard-0.2.0-rc1.apk
```
