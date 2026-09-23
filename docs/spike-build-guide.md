# spike-build-guide（A1-1 本地构建指南）

> 版本：`v0.1`
> 最后更新：2026-09-21
> 责任人：成员 A
> 对应任务：A1-1
> 关联：`docs/spike-results.md`、`docs/network-core-map.md`、`scripts/build-trackercontrol-spike.sh`、`THIRD_PARTY_NOTICES.md`
> 约定：第三方源码**不导入本仓库**，保留在外部，只登记固定 commit。

---

## 1. 目标

在 A 的本地构建机上，按固定版本构建 TrackerControl，并验证：

1. 能构建出 APK；
2. 演示机安装后启动 VPN 仍可联网；
3. 产出连接事件/阻断记录（后续 A1-5/A1-6）。

止损：24h 内必须构建成功并联网；否则换固定旧 tag，仍失败则收缩网络范围或转 MIT 备选。

---

## 2. 固定版本

| 项 | 值 |
|---|---|
| tag | `2026080501` |
| commit | `9504d41b9f6fa1509d784e5503c084d4b428307d` |
| 许可证 | GPL-3.0 |

---

## 3. 环境要求（来自该 commit 静态读取，可能随构建报错调整）

| 组件 | 建议版本 | 说明 |
|---|---|---|
| JDK | 17+ | AGP 9.3.1 要求较新 JDK，按报错调整 |
| Android SDK | compileSdk 37 / targetSdk 37 | 需在 SDK Manager 安装对应 platform 与 build-tools |
| NDK | 以 `app/build.gradle` 的 `wgbridgeNdkVersion` 为准 | JNI/CMake 必需 |
| CMake | 以 `app/build.gradle` 为准 | |
| Rust | 1.95.0（仅 WireGuard 可选链） | `rust-toolchain.toml`；非必需可先不装 |
| Gradle | 9.6.1（wrapper 自带） | 用 `./gradlew`，勿用系统 gradle |

> 当前仓库开发环境（无 JDK/Android SDK）不适合构建，必须在 A 本机进行。

---

## 4. 步骤

### 4.1 一键脚本（推荐）

```bash
# 在 CausalGuard 仓库根目录
chmod +x scripts/build-trackercontrol-spike.sh
./scripts/build-trackercontrol-spike.sh
# 或指定工作目录
./scripts/build-trackercontrol-spike.sh "$HOME/work/tc-spike"
```

脚本会：检查环境 → clone 并切到固定 commit → 校验 commit → `assembleFdroidDebug` → 列出 APK。

### 4.2 手动步骤

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

git clone https://github.com/TrackerControl/tracker-control-android.git
cd tracker-control-android
git checkout 9504d41b9f6fa1509d784e5503c084d4b428307d

# 演示用 fdroid flavor，避免 Play 依赖
./gradlew --no-daemon assembleFdroidDebug
find app/build/outputs -name "*.apk"
```

安装到演示机：

```bash
adb install -r app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
```

---

## 5. 需要回填 `docs/spike-results.md` 的信息

- 2.3 节：主机 OS、JDK、SDK/Build-Tools、NDK、是否 Rust、演示机型号/Android 版本
- 2.3 节：构建命令、结果、APK 路径
- 3 节 A1-1：VPN 启动后能否联网（截图/日志路径）

---

## 6. 常见失败与降级

| 现象 | 处理 |
|---|---|
| JDK 版本不兼容 | 换 JDK 17/21，按 AGP 报错提示 |
| 找不到 compileSdk 37 | SDK Manager 安装对应 platform/bulid-tools |
| NDK/CMake 缺失 | 安装 `app/build.gradle` 指定 NDK 与 CMake |
| Rust/WireGuard 构建失败 | 先构建不含 WG 的 flavor/配置；P0 不强依赖 WG |
| 依赖下载慢/失败 | 配置镜像；离线用 `--offline` 配合本地缓存 |
| 24h 仍无法构建 | 换更旧固定 tag；仍失败则收缩网络范围或评估 MIT 备选（docs/20 第 4.5/13 节） |

---

## 7. 许可证合规（构建阶段）

- 第三方源码保留在仓库外，**不提交进 CausalGuard**；
- 固定 commit 已登记在 `THIRD_PARTY_NOTICES.md`；
- 若后续需要团队修改底座，修改必须单独 commit 并登记修改文件；
- 答辩材料不得把底座能力声称为团队原创。
