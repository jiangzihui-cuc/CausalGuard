# A8-2 构建说明、依赖版本与源码包（不依赖 B 的部分）

> 状态：进行中（构建说明/依赖版本/源码打包脚本已完成；最终 release APK 与源码包待 B 合入后冻结）
> 责任人：成员 A
> 关联任务：[17 任务看板](17-task-board.md) A8-2；[15 验收清单](15-acceptance-checklist.md) §5、§7；[16 演示与发布方案](16-demo-and-release-plan.md) §6、§7；[A7 RC 记录](a7-release-candidate.md)。
> 边界：本文件不含最终 APK/SHA（待 A8-1/A8-5 冻结），只固化**构建环境、依赖版本、构建步骤、源码包**这些与 B 代码无关的提交材料。

---

## 1. 构建环境与依赖版本

固定版本，禁止浮动 `main`；与 [A7 RC 记录](a7-release-candidate.md) §1.2 一致。

| 组件 | 版本 | 来源 |
|---|---|---|
| 构建宿主 | Windows 11 + WSL2（`Linux 5.15.x-microsoft-standard-WSL2`，amd64） | 本地 |
| JDK | Eclipse Adoptium Temurin `17.0.13+11` | `JAVA_HOME=$HOME/tools/jdk-17.0.13+11` |
| Gradle | `9.6.1` | `gradle/wrapper/gradle-wrapper.properties`（腾讯镜像，可替换官方地址） |
| Android Gradle Plugin | `9.4.1` | `build.gradle`（root） |
| Kotlin / stdlib | `2.2.10`（AGP 9 内置，不引入独立 KGP） | `build.gradle` |
| KSP | `2.3.4` | `build.gradle` |
| compileSdk / targetSdk | `37` | `app/build.gradle` |
| minSdk | `29`（Android 10 基线） | `app/build.gradle` |
| Android SDK Platform | `android-37.0` | `$ANDROID_HOME/platforms` |
| Android SDK Build-Tools | `37.0.0`（含 `apksigner`/`zipalign`） | `$ANDROID_HOME/build-tools` |
| Android SDK Platform-Tools | `37.0.1`（含 `adb`） | `$ANDROID_HOME/platform-tools` |
| Android NDK | `27.2.12479018`（仅底座原生构建） | `$ANDROID_HOME/ndk` |
| 网络底座 submodule | tag `2026080501` / commit `9504d41b9f6fa1509d784e5503c084d4b428307d` | `third_party/tracker-control-android` |
| 关键库 | Room `2.7.0`、Compose BOM `2026.09.00`、Retrofit `3.0.0`、OkHttp `4.12.0`、kotlinx.serialization `1.9.0`、coroutines `1.9.0` | 见 [A7 依赖许可证报告](a7-license-report.md) |

> 完整去重依赖清单：`scripts/generate-license-report.sh` → `build/license-report/resolved-artifacts.txt`。

---

## 2. 构建步骤（干净环境）

```bash
# 1) 工具链：JDK17 + Android SDK(platform 37 / build-tools 37.0.0)
export JAVA_HOME=$HOME/tools/jdk-17.0.13+11
export ANDROID_HOME=$HOME/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

# 2) 拉取源码与底座
git clone <repo-url> CausalGuard && cd CausalGuard
git submodule update --init --recursive

# 3) 本地 SDK 路径（不入库）
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 4) 构建 debug（含测试与 lint）
./gradlew :app:assembleDebug
./gradlew test
./gradlew :app:lintDebug
```

> 有 CI 时以 `.github/workflows/ci.yml` 为准；本地无底座 native 工具链时，应用层模块仍可独立编译与测试。

---

## 3. 发布 APK 构建与签名

```bash
# 前置：release keystore 与密码（不入库）
#   $HOME/causalguard-release/keystore.properties
#   KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
scripts/build-release-rc.sh          # :app:assembleRelease → zipalign → apksigner sign
```

脚本输出 `versionName`/`versionCode`、commit SHA、APK 路径与 SHA-256（见 A7 RC 记录 §2 的样例格式）。签名方案、证书 DN 与指纹随最终提交归档。

---

## 4. 测试与 lint（发布门禁）

| 检查 | 命令 | A7 基线结果（2026-10-07） |
|---|---|---|
| 单元测试 | `./gradlew test` | 261 通过 / 0 失败 / 0 错误 |
| lint | `./gradlew :app:lintDebug` | 0 error / 4 warning（非阻断） |

> 最终提交前以 `main` 冻结 commit 重跑一次，结果记入最终检查表。

---

## 5. 源码包

```bash
scripts/package-source.sh [revision] [输出目录]   # 默认 HEAD → build/source-package/
```

产出 `CausalGuard-src-<version>-<shortsha>.tar.gz`，包含：

| 内容 | 说明 |
|---|---|
| 主仓库受控文件 | `git archive` 导出，仅**受控文件**；密钥/真实数据/临时文件因 `.gitignore` 天然排除 |
| `third_party/tracker-control-android/` | 网络底座对应源码（submodule 固定 commit 的归档） |
| `third_party/patches/*.patch` | 团队对底座的补丁（对应源码 = 固定 commit + 补丁） |
| `SOURCE_MANIFEST.txt` | revision、submodule commit/tag、补丁 SHA-256、许可证文件存在性、复现命令 |

> 最终提交包须在 **A8-5 最终 tag** 建立后，以该 tag 为 `revision` 重新生成一次，保证与 APK 版本一致。

---

## 6. 可复现性

- **依赖锁定**：Gradle wrapper 固定 `9.6.1`，所有 Maven 依赖显式版本（见 §1）。
- **APK 哈希**：AGP 会在 APK 内嵌 `META-INF/version-control-info.textproto`（含 revision），因此**不同 commit 重建哈希不同**；复现须 checkout 记录的源码 commit。
- **确定性**：同一 commit 清空 `:app:clean` 重建，未签名 APK SHA-256 一致（A7 已验证）。
- **底座**：以固定 commit + 补丁交付，非 vendored 进主仓历史。

---

## 7. 待 B 合入后冻结的事项

- [ ] B 的阶段 6/8 分支（B6-x、B8-x）合入 `main` 后，重新生成最终 release APK 与源码包；
- [ ] 更新 `versionName`/`versionCode` 到最终版本；
- [ ] 以最终 tag 重新运行 `scripts/package-source.sh <tag>` 与 `scripts/verify-gpl-compliance.sh --tag <tag>`；
- [ ] 最终 APK SHA-256、源码包 SHA-256、构建 commit 记入最终检查表。
