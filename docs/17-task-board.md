# 17 任务看板

> 版本：`v0.13`
> 最后更新：2026-10-07
> 责任人：成员 B（协作：成员 A）
> 状态枚举：未开始 / 进行中 / 待验证 / 已完成 / 阻塞
> 依据：[21 并行分工与协作规范](21-parallel-work-allocation-plan.md)。阶段内任务按 A（平台/网络/系统/发布）与 B（产品/规则/证据/UI/评测）两条并行链拆分；每人每天最多保留“今日必须完成 1 项 + 完成后再做 1 项 + 阻塞替代 1 项”。
>
> **看板同步说明（回填）**：按仓库实际状态校正阶段 0~3 的 B 侧条目——`B1-4`、`B1-6` 实际已完成；`B2-1`~`B2-5`、`T2-4`、`T2-5` 已有部分产出但未达验收，改为「进行中」；`B3-1`~`B3-7` 已完成（`B3-6` 经 PR #19、`B3-5` 经 PR #20 均已合并入 `main`）。A 侧阶段 0~3 无缺口。

## 阶段 0：项目启动与范围冻结（9/20）

| 编号 | 任务 | 主责 | 协作 | 状态 |
|---|---|---|---|---|
| T0-1 | 通读技术方案与设计文稿 | 两人 | - | 已完成 |
| T0-2 | 确定 Android 10/API 29+、真实/沙箱模式 | 成员 A | 成员 B | 已完成 |
| T0-3 | 确定主演示案例 | 两人 | - | 已完成 |
| T0-4 | 建立 Git 仓库、分支规则与 docs/ 目录 | 成员 A | 成员 B | 已完成 |
| T0-5 | 项目章程与范围排除表 | 成员 B | 成员 A | 已完成 |
| T0-6 | 任务看板与风险清单 | 成员 B | 成员 A | 已完成 |
| T0-7 | 设计基线 01~16 已形成 v0.1 草案；阶段 1 Spike 后修订，阶段 2 正式冻结跨模块契约 | 两人 | - | 进行中 |
| T0-8 | 确认 TrackerControl/NetGuard GPL 路线并建立 THIRD_PARTY_NOTICES 模板 | 成员 A | 成员 B | 已完成 |

## 阶段 1：技术可行性 Spike（9/21-9/22）

### 成员 A：高风险真实能力

| 编号 | 任务 | 交付物 | 状态 |
|---|---|---|---|
| A1-1 | 固定 TrackerControl commit/tag 并构建 | commit、环境记录、可联网 APK | 已完成 |
| A1-2 | 定位 VPN、连接、DNS、UID、阻断入口 | `docs/network-core-map.md` | 已完成 |
| A1-3 | PackageManager Spike | 日志/JSON：App、UID、版本、声明权限、授权状态 | 已完成 |
| A1-4 | UsageStats Spike | 前后台或明确 unknown/失败原因 | 已完成 |
| A1-5 | 输出最小 NetworkEvent | 脱敏 JSON：时间、协议、IP/域名线索、端口、UID/unknown | 已完成 |
| A1-6 | 最小阻断验证 | 日志：至少一个测试域名可阻断并保留尝试记录 | 已完成 |
| A1-7 | 开源技术登记 | [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) 条目 | 已完成 |
| A1-8 | 记录 UID 归属成功率 | 更新 [02 能力边界表](02-android-capability-matrix.md) | 已完成（量化成功率已补测，见 A4-4） |

> 阶段 1 进展（A 回填，证据见 [Spike 结果](spike-results.md)）：**A1-1～A1-8 全部完成**。A1-8 结论已出——普通 App 直接调用 `getConnectionOwnerUid` 恒为 `-1`，归属必须复用底座 VpnService 内路径，`docs/02` 已更新，失败降级为 unknown。底座 UID 归属**量化成功率**已于 2026-09-28 在真机 PJW110 / Android 16 补测（TCP/UDP 231/231 = 100%，见 A4-4 与 `docs/spike-results.md` §3）。

止损：24 小时内 TrackerControl 必须能构建并联网；48 小时内必须得到连接事件或明确失败原因；失败先换固定旧 tag，仍失败则缩小网络范围。

### 成员 B：完全不等待 VPN

| 编号 | 任务 | 交付物 | 状态 | 进展/证据 |
|---|---|---|---|---|
| B1-1 | 定义 v0.1 事件 fixture | `docs/fixtures/`（8~12 条 JSON） | 已完成 | [privacy-events-v0.1.json](fixtures/privacy-events-v0.1.json) 已覆盖 10 条事件、正常/风险/unknown/Demo；旁路预期与规则资产同步完成并校验。 |
| B1-2 | 定义 4 个 Demo 场景 | `docs/demo-scenarios.md` | 已完成 | [demo-scenarios.md](demo-scenarios.md) 已通过 PR #7 合入，覆盖操作、真值、预期证据链和失败降级。 |
| B1-3 | 证据文案模板 | 模板 JSON/文档：事实、能力、推断、不可观测不混写 | 已完成 | [explanation-templates-v0.1.json](fixtures/explanation-templates-v0.1.json) 已与 10 条事件逐一关联；正常、风险、unknown 和 Demo 场景均有明确解释边界，unknown 不生成确定处置。 |
| B1-4 | 第三方组件登记 | [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) 汇总 | 已完成 | `THIRD_PARTY_NOTICES.md` 已登记 TrackerControl Android（commit `9504d41b`，GPL-3.0，含补丁/本地修改）、`kotlinx.serialization 1.9.0`、Compose BOM + AndroidX，以及构建/运行时依赖（AGP/Gradle/Kotlin/SDK/NDK/CMake/Rust/wgbridge-rs 等）。 |
| B1-5 | UI 状态草图 | 页面状态表：loading/empty/unknown/demo/error | 未开始 | 尚未形成页面状态表；建议与 B1-2 一并落到 `docs/demo-scenarios.md`。 |
| B1-6 | Android 能力边界表定稿 | [02-android-capability-matrix.md](02-android-capability-matrix.md) | 已完成 | `docs/02` 技术事实已按阶段 1 Spike 定稿（A2-1，2026-09-24），UID 归属量化成功率 231/231=100% 已回填。 |
| B1-7 | 真实/沙箱模式产品说明 | [01 项目章程](01-project-charter.md) 第 4 节 | 已完成 | 已明确真实观测模式与演示沙箱模式，并要求界面、数据模型和答辩材料区分演示数据。 |

唯一集成点：A 导出的真实 NetworkEvent 必须可映射到 B 的 fixture schema；不一致只改 Adapter 或契约，不重写 VPN、规则或 UI。

阶段 1 成员 B 进度快照（2026-09-24）：核心离线资产已完成 `B1-1`、`B1-3`、`B1-7`；`B1-4` 进行中，`B1-6` 待 A 的 Spike 结果验证；当前剩余首选项为 `B1-5`。阶段 1 总体门禁仍取决于 A 侧 VPN/NetworkEvent 验证，不能提前标记完成。

## 阶段 2：需求、架构与数据设计冻结（9/23-9/24）

| 编号 | 任务 | 主责 | 协作 | 状态 |
|---|---|---|---|---|
| A2-1 | 按 Spike 更新能力矩阵技术事实 | 成员 A | 成员 B | 已完成 |
| A2-2 | 冻结 `PrivacyEvent`、公共枚举与 `schemaVersion` | 成员 A | 成员 B | 已完成 |
| A2-3 | 建立 `core-model`、Repository/Adapter 接口骨架 | 成员 A | 成员 B | 已完成 |
| A2-4 | 修订 Room schema（实现延后到阶段 3） | 成员 A | 成员 B | 已完成 |
| A2-5 | 冻结 TrackerControl Adapter 边界 | 成员 A | 成员 B | 已完成 |
| A2-6 | 确定 Compose、依赖注入与模块结构 | 成员 A | 成员 B | 已完成 |
| B2-1 | 冻结 PRD 的 P0/P1/P2 | 成员 B | 成员 A | 进行中（P0/P1/P2 已在 `docs/01` §5/§6 枚举；`docs/04` 尚缺冻结标记） |
| B2-2 | 冻结 `RiskAssessment`、`ExplanationResult` | 成员 B | 成员 A | 进行中（`RiskAssessment` 已定型；`ExplanationResult` 尚未在 `:core-model` 落地） |
| B2-3 | 5 条 P0 规则定义、场景知识草案、解释模板 | 成员 B | 成员 A | 进行中（`risk-rules-v0.1.json` 9 条、解释模板 10 条已齐；缺独立场景知识资产） |
| B2-4 | 补全权限拒绝/无域名/无法归属/断网等 UI 状态 | 成员 B | 成员 A | 进行中（unknown/无法归属已处理；权限拒绝/无域名/断网无专门状态） |
| B2-5 | 建立首批 20 条评测样例 | 成员 B | 成员 A | 进行中（现 10 条事件/预期，`docs/14` 仅 2 条示例） |
| T2-4 | 三类契约冻结 v0.1（独立 PR） | 两人 | - | 进行中（`docs/09` 已冻结；`docs/10` 半冻结、`docs/11` 未冻结） |
| T2-5 | 更新被 Spike 推翻的 05~15 文档 | 两人 | - | 进行中（`02/05/06/07/09/10/14` 已更新；`11/12/13/15/16` 未更新） |

> 阶段 2 进展（A 回填，2026-09-24）：A2-1～A2-6 已完成并合入 `main`——`feature/a-t2-event-contract`（A2-2/A2-4：docs/07/08/09）、`feature/a-t2-arch-freeze`（A2-1/A2-5/A2-6：docs/02/05/06 + `docs/trackercontrol-adapter-boundary.md`）、`feature/a-t2-core-model`（A2-3：`:core-model` 模块与接口骨架，含契约 JSON 解析测试）。`docs/08` 的 Room 实现延后到阶段 3。B2-1～B2-5、T2-4/T2-5 仍待 B/两人推进。

## 阶段 3：MVP 基础闭环（9/25-9/27）

### 成员 A：数据基础设施

| 编号 | 任务 | 验收 | 状态 |
|---|---|---|---|
| A3-1 | Room Entity、DAO、migration | 写入、查询、重启持久化测试通过 | 已完成 |
| A3-2 | `EventRepository` 与事件导入器 | 可批量导入 B 的 fixture | 已完成 |
| A3-3 | AppProfile/PermissionState Repository | Fake 与真实 Provider 可替换 | 已完成 |
| A3-4 | 导航/ViewModel 注入接口 | 不包含页面视觉和业务文案 | 已完成 |
| A3-5 | DAO、Adapter、Repository 单元测试 | CI 通过 | 已完成 |

> 阶段 3 进展（A 回填，2026-09-24）：A3-1～A3-5 已实现并合入 `main`（merge `feature/a-t3-room-repository`）——`:app` 新增 Room 实体/DAO/数据库与迁移登记（schema 导出到 `app/schemas/1.json`）、`PrivacyEventRepository` 等 Room 实现、`EventImporter` 导入 docs/09 fixture、真实/可替换 Provider 与 `AppContainer` 注入边界。单测 13 项通过（含 Robolectric Room 写入/查询/幂等/unknown 降级/重开持久化）。构建采用 KSP 2.3.4 + Room 2.7.0（与 AGP 9 内置 Kotlin 兼容；旧 KSP 2.2.x 与 KGP 均不可用，见 18 风险清单 RK-21）。B3-4～B3-6 仍待 B。

### 成员 B：可运行产品闭环

| 编号 | 任务 | 验收 | 状态 |
|---|---|---|---|
| B3-1 | FakeEventRepository | 无 VPN 也能播放 fixture | 已完成 |
| B3-2 | 首页、时间线、详情、设置四个 P0 页面 | Fake 数据可完整浏览 | 已完成 |
| B3-3 | 小型规则执行器与 5 条规则 | 正例、反例、unknown 测试通过 | 已完成 |
| B3-4 | 证据卡片和本地解释模板 | 事实/推断/不可观测显示准确 | 已完成 |
| B3-5 | Demo App 场景 A/B | 可单独编译、触发和复位 | 已完成 |
| B3-6 | 第一批评测测试 | 至少 20 条自动运行 | 已完成 |
| B3-7 | 规则引擎契约收敛：复用 `:core-model` 类型、实现 `RiskRuleEngine.assess(RuleInput)`、包名改 `com.causalguard.rules`、纳入根构建 | 规则模块依赖 `:core-model`，`gradle build` 通过 | 已完成 |

集成：`fixture → FakeEventRepository → 规则 → 证据 → 本地解释 → UI → Fake 处置`；随后只替换 `FakeEventRepository → RoomEventRepository`。
> 阶段 3 成员 B 规则引擎进展（2026-09-28）：B3-3、B3-7 已完成。feature/b-rule-engine-core 已完成确定性规则执行器和 v0.1 规则资产，复用 :core-model 的 RuleInput / RiskAssessment，RuleEvaluator 实现 RiskRuleEngine，标准 assess() 返回 canonical RiskAssessment，rich evaluation metadata 由 evaluate() 提供；包名统一为 com.causalguard.rules，模块纳入根 Gradle 构建并移除旧 standalone Gradle 入口。当前 rule-engine unit test、lint 及根 gradle build 均通过。
> B3-2 页面进展（2026-09-30）：Home 完成；Timeline 完成；Detail 完成；Settings 完成。Settings 在 Fixture 模式中只展示真实可用/不可用能力，不伪造实时监测、AI、保留或删除行为。
> B3-5 进展（2026-10-04）：Demo Map 真机前台 LocationManager 成功；Demo Calculator 真机 `onStop` 后台 clipboard probe 在 Android 16/API 36 被平台拒绝，按 `Platform Restricted` 正常降级且不生成事件；两场景 Reset 均通过。
> B3-6 进展（2026-10-02）：首批 25 条规则评测样例可自动执行，覆盖正常、风险、场景不匹配、unknown、多规则与 no-match；输入、上游 context 与 oracle 分离。
> B3-1 已完成：新增纯内存 FakePrivacyEventRepository，通过 canonical PrivacyEventRepository 播放冻结 v0.1 fixture；无需 VPN/Room，专项与回归测试通过。

> 契约收敛说明（A，2026-09-24）：`RiskAssessment` 已按 docs/10 §2 在 `:core-model` 补齐 `matchedRules`/`category`，并新增 `RuleInput`/`RuleUsageContext`/`RiskRuleEngine`（`core-model/.../RuleApi.kt`）。B 侧需删除自建枚举与模型、改用 core-model 类型，规则动作建议改名 `RuleRecommendation` 以避开 docs/07 §2.7 的持久化 `Recommendation`。根 `settings.gradle` 已守卫式 include `:rule-engine`，B 合并分支时需同步其 `build.gradle.kts`（JDK17、依赖 `:core-model`、移除独立 `settings.gradle.kts` 与 `FAIL_ON_PROJECT_REPOS`）。

## 阶段 4：真实数据接入（9/28-9/30）

| 编号 | 任务 | 主责 | 协作 | 状态 |
|---|---|---|---|---|
| A4-1 | PackageManager Provider | 成员 A | - | 已完成（A3-3 `PackageManagerProfileProvider`） |
| A4-2 | UsageStats Provider | 成员 A | - | 已完成（A3-3 `UsageStatsContextProvider`） |
| A4-3 | TrackerControl/NetGuard Network Adapter | 成员 A | - | 已完成（跨进程显式包名广播 `setPackage("com.causalguard")` + App 侧 `RECEIVER_EXPORTED`；2026-09-29 真机 PJW110/Android 16 端到端验证通过，486 条事件入库，见 `docs/spike-results.md` §3 A4-3） |
| A4-4 | UID/包名/域名/时间窗口关联（含补 A1-8 遗留：adb 抓 `Get uid=` 统计底座归属量化成功率） | 成员 A | - | 已完成（关联入库链路 `NetworkEventCollector` 已接 `AppContainer`；A1-8 量化成功率 231/231=100%；2026-09-29 真机端到端随 A4-3 验证通过：486 条 `network_event`/`privacy_event` 一一对应、414 条归属、353 条有域名线索） |
| A4-5 | VPN 生命周期、前台服务、网络切换和异常恢复 | 成员 A | - | 已完成（`dataSync` 前台服务 `NetworkMonitorService` + `NetworkMonitorController`（Mutex 串行化 restart）+ `AndroidConnectivityWatcher`（基线抑制/800ms 去抖）；2026-09-29 真机 PJW110/Android 16 验证：VPN 回收/恢复各触发一次 restart、`active=true collecting=true`、事件 486→510 持续入库、停止后服务注销。见 `docs/spike-results.md` §3 A4-5） |
| B4-1 | 只选一套 tracker 数据，固定版本并生成 50~200 条精简离线表 | 成员 B | 成员 A | 已完成 |
| B4-2 | `TrackerClassifier` 和域名归一化 | 成员 B | 成员 A | 已完成 |
| B4-3 | 无权限/无域名/无法归属/VPN 停止/真实沙箱标签 | 成员 B | - | 已完成 |
| B4-4 | 使用 A 的真实 fixture 校准规则 | 成员 B | 成员 A | 已完成 |
| B4-5 | Demo App 场景 C/D | 成员 B | - | 已完成 |

> B4-1 进展（成员 B，2026-10-04）：固定 TrackerControl `9504d41b` 中的 Disconnect bundled snapshot，生成 100 条 deterministic offline tracker/domain 数据，许可证与 provenance 已登记。

> B4-2 进展（成员 B，2026-10-05）：完成离线 tracker asset loader、严格域名归一化和基于 DNS label boundary 的 exact/parent/longest-match 分类，未改 canonical contract 或风险规则。

> B4-3 进展（成员 B，2026-10-05）：完成真实/演示/Fixture/未知来源标签与事件级观测降级；无域名、无法归属、Usage Access 未授权和监测暂停均保持诚实表达，未改变风险结论或采集生命周期。

> B4-4 第一切片进展（成员 B，2026-10-06）：真实 A4 fixture 已建立独立事实 oracle 并完成首轮回归；15 个可见真实域名在 B4-1 精简数据集中 0 命中，已完成固定 upstream/采样覆盖诊断，结果为 mixed，后续再决定规则接入方式。

> B4-4 最终收口（成员 B，2026-10-06）：20 条 2026-10-05 PJW110 / Android 16 真机真实输入已配套独立 fact oracle；tracker coverage diagnosis 为 mixed（runtime 0/15，固定完整 upstream 1/15，14/15 source coverage gap、1/15 sampling/selection gap）；运行时采用 rules-v0.2 完成 R-008 attribution calibration，rules-v0.1 frozen baseline 保留。用户在正常 WSL 完成 `:rule-engine:testDebugUnitTest` + `:app:testDebugUnitTest`，BUILD SUCCESSFUL（46s）。

> B4-5 最终验收（成员 B，2026-10-05）：在 PJW110 / Android 16（API 36）完成两轮真机验证。DEMO-C 中 Demo Calculator 在后台执行最小 TCP probe，CausalGuard VPN 真实观察到 `com.demo.calculator → example.com:443`、`TCP`、`domainHint=example.com`；应用层最终为 `ConnectException`，ColorOS 后台限制导致 socket 被关闭。因此“VPN 已观察到连接尝试”和“`Socket.connect` 最终成功”是两个不同事实；本次不发送 payload，也不要求 tracker 命中。DEMO-D 中真实记录 granted baseline；往返 `ACTION_APPLICATION_DETAILS_SETTINGS` 时 ColorOS 销毁 Activity，baseline 通过最小 `SharedPreferences` session 恢复，当前 permission 仍由 `checkSelfPermission()` 实时读取并成功确认 revoked；`Armed → Home` 后真实执行 revoked-location probe，最终为 `Failure(IllegalStateException)`，原因是撤权后 `getProviders(true)` 返回空，现有 no-provider 分支触发 `error("No enabled location provider")`。这不是权限绕过，不生成 `PrivacyEvent`，不保存或展示坐标、accuracy 或轨迹；Reset 会清除 baseline session。B4-5 真机验收 PASS。

阶段门：至少一种真实网络事件进入 Room；至少一个 App 获得使用上下文；无域名/UID 时诚实降级；真实 Provider 替换 Fake 后规则和 UI 无需重写。

> 阶段 4 进展（A 回填，2026-09-27）：A4-1/A4-2 已在 A3-3 `RealProviders` 落地；A4-4 完成 `NetworkEvent → PrivacyEvent` 关联入库链路——`data/ingest/NetworkEventIngestor`（补齐 `schemaVersion`/`dedupKey`/时间窗、`evidenceLevel=E2`、`source=vpn`、诚实保留 `uid=-1`/`packageName=unknown`）、`data/network/ReplayNetworkEventSource`（无 VPN 回放）、`data/repository/RoomEventSink`（docs/09 §3 唯一写入口，已接入 `AppContainer`），并有 8 项单测覆盖转换/降级/去重窗/回放/端到端。同时修复 `RiskAssessment` 持久化漂移：Entity/Mapper/`docs/08` 补 `category`、`matchedRules`，数据库升 v2 并登记 `MIGRATION_1_2`。A4-3 底座源码已以 git submodule 固定导入 `third_party/tracker-control-android/`（commit `9504d41b`）；`data/network/trackercontrol/` 已实现回调桥（`PacketMeta`/`DnsRecordMeta`/`TrackerControlCallback`）与 `TrackerControlNetworkAdapter`（协议号映射、`blocked`、`dnsResolved`→`domainHint`、`uid=-1`→`unknown`、有界队列丢包计数、`start/stop` 生命周期），9 项单测覆盖；真实 `ServiceSinkhole` 挂接需底座构建与真机。A4-5（VPN 生命周期/前台服务）当时仍待真机验证，已于 2026-09-29 完成（见本条下文与 A4-5 回填）。
>
> 真机进展（A 回填，2026-09-28）：演示机 **PJW110 / Android 16（API 36）** 已通过 WSL2 无线调试接入 adb，并侧载 fdroid debug 底座（`net.kollnig.missioncontrol.fdroid.test`），VPN 正常起 `tun0`。借此补齐 A1-8 遗留的**底座 UID 归属量化成功率**：从 `TrackerControl.VPN` 日志（`ServiceSinkhole.getUidQ`）统计 TCP/UDP 归属 **231/231 = 100%**（两轮：单 App 137/137、多 App 94/94，覆盖闲鱼/番茄小说/微信/支付宝/企业微信），ICMP 固定不归属按 unknown 降级，与 `docs/02`/`docs/09` 一致。下一步：在该底座内挂接 `TrackerControlCallback` 打通 A4-3 真实事件端到端。
>
> A 侧阶段 4 已合入分支登记：`feature/a-t4-network-ingest`→`24e71a9`（A4-4 关联入库 + DB v2）、`feature/a-t4-trackercontrol-submodule`→`990e96c`（A4-3 底座 submodule）、`feature/a-rule-contract-extension`→`4224cdf`（规则契约扩展）、`feature/a-t3-room-repository`→`3479330`（A3 数据层）。当前 `:app` 单测 31 项通过，`gradle build` 通过（CI 同）。
>
> A4-3/A4-4 代码进展（A 回填，2026-09-28）：采用**广播桥接**（底座独立 Gradle 工程无法 import `com.causalguard.*`，冻结的 `TrackerControlCallback` 契约不变，详见 [adapter 边界](trackercontrol-adapter-boundary.md) §9）。App 侧新增 `data/network/trackercontrol/TrackerControlBroadcast`（广播契约 + 纯解析）、`TrackerControlEventReceiver`、`TrackerControlEventSource`，及 A4-4 端到端 `data/ingest/NetworkEventCollector`（`NetworkEventSource → NetworkEventIngestor → Room`，`start/stop` 幂等），已接入 `AppContainer`。底座侧不改 submodule，改以补丁交付：`third_party/patches/a4-3-serversinkhole-network-hook.patch`（新增 `eu.faircode.netguard.CausalGuardNetworkHook`，在 `ServiceSinkhole.logPacket`/`dnsResolved` 各挂一处）与 `scripts/apply-trackercontrol-hook.sh`（应用/`--revert`）；submodule 保持干净、`git apply --check` 通过。`:app` 单测 41 项通过（新增 Broadcast 5 / EventSource 3 / Collector 2）。
>
> **进程边界修正（A 回填，2026-09-28）**：底座与 CausalGuard 是两个已安装 APK、两个进程，第一版 `LocalBroadcastManager`（仅同进程）真机收不到事件。已将桥接改为**跨进程显式包名广播**：底座 `sendBroadcast` + `intent.setPackage("com.causalguard")`；App 侧 `TrackerControlEventSource` 改用 `Context.registerReceiver(..., RECEIVER_EXPORTED)`（API 33+ 显式导出标志），移除 `androidx.localbroadcastmanager` 依赖，`TrackerControlBroadcast` 增 `TARGET_PACKAGE` 常量，单测改用普通广播 + Robolectric looper idle。`MainActivity` 增加「启动/停止网络采集」按钮作为真机验证入口（生产生命周期属 A4-5）。`:app` 单测 41 项全部通过。真机端到端与 VPN 生命周期（A4-5）当时尚未验证，均已于 2026-09-29 完成（见下文）。
>
> **A4-3 真机验证完成（A 回填，2026-09-29）**：按上述 6 步在演示机 **PJW110 / Android 16（API 36）** 执行完毕。底座（`net.kollnig.missioncontrol.fdroid.test`，已应用 `scripts/apply-trackercontrol-hook.sh` 补丁并确认 `CausalGuardNetworkHook` 编入 `classes7.dex`）VPN 起 `tun0`；CausalGuard（`com.causalguard`）点「启动/停止网络采集」，产生 Chrome/闲鱼/微信/淘宝等真实 TCP/UDP/DNS 流量。结果：**486 条 `network_event` 与 486 条 `privacy_event` 一一对应入库**，`source=vpn`、`evidenceLevel=E2`，414 条归属到包名、353 条有 `domainHint`（60 个不同域名）、2 条 DNS 阻断（`UDP:53 blocked=1`）。降级证据：72 条 `uid=-1`（ICMP 39 + TCP 33，含 IPv4 14/IPv6 19）诚实保留 `packageName=unknown`；去掉 `dedupKey` 按 `docs/09` §4 仅做查询期聚合、不删原始留存。详见 [spike-results](spike-results.md) §3 A4-3。**A4-3/A4-4 真机端到端完成**。
>
> **A4-5 完成与合并回归修复（A 回填，2026-09-29）**：新增 `data/ingest/NetworkCollector`（采集契约）与 `NetworkMonitorController`（`start/stop/restart` 经 `Mutex` 串行化、异常回调后仍可重试），`service/NetworkMonitorService`（`START_STICKY` + `FOREGROUND_SERVICE_TYPE_DATA_SYNC` 常驻通知，回调 800ms 去抖）与 `service/AndroidConnectivityWatcher`（`registerDefaultNetworkCallback`，忽略注册基线）。真机 **PJW110 / Android 16** 验证：VPN 回收（`force-stop` 底座）与恢复各触发一次 `network restart done: active=true collecting=true`，事件在 Room **486→495→510** 持续入库，停止后 `dumpsys` 无 ServiceRecord。同时修复合并 `origin/main` 引入的回归：`AppContainer.privacyEventRepository` 曾取 main 的 `FakePrivacyEventRepository` 默认值，使采集只写内存、Room 不增长；已恢复 `RoomPrivacyEventRepository(database)`（对齐本阶段门“真实网络事件进入 Room”与 Room 唯一写入口）。`:app` 单测 59 项通过。详见 [spike-results](spike-results.md) §3 A4-5。

## 阶段 5：场景推理、因果链与处置复查（10/1-10/3）

### 成员 A：执行层

| 编号 | 任务 | 状态 |
|---|---|---|
| A5-1 | 实现真实 `MitigationExecutor` | 已完成（底座补丁已实现并应用，2026-10-05 真机验证通过） |
| A5-2 | P0 只保证域名阻断，App 级阻断视稳定性进入 P1 | 已完成 |
| A5-3 | 系统设置跳转 | 已完成 |
| A5-4 | 处置记录和观察窗口持久化 | 已完成 |
| A5-5 | 按 App/域名/时间窗提供前后聚合查询 | 已完成 |
| A5-6 | 区分“没有请求”和“有请求但已阻断” | 已完成 |

### 成员 B：原创分析层

| 编号 | 任务 | 状态 |
|---|---|---|
| B5-1 | 场景知识库和场景一致性 | 已完成 |
| B5-2 | `EvidenceLink` 与证据链构建器 | 已完成 |
| B5-3 | 因果链节点和事实/推断等级 | 已完成 |
| B5-4 | Recommendation 选择 | 已完成 |
| B5-5 | `RecheckComparator`：减少、无变化、被阻断、无法确认 | 已完成 |
| B5-6 | 因果链、处置和复查页面 | 已完成 |
| B5-7 | 至少 8 条处置前后评测样例 | 已完成 |

集成契约：`B：Recommendation → A：MitigationExecutor → A：MitigationRecord + 新事件 → B：RecheckResult + 页面`。

> **阶段 5 B 侧全部完成（成员 B，2026-10-09）**：B5-1~B5-7 全部完成，覆盖场景一致性、证据链、因果链、Recommendation 选择、确定性复查比较、因果链/处置/复查页面与 12 条处置前后评测样例。与 A 的集成契约已由 B5-6 页面链路与 B5-7 评测集闭环验证。B5-1 场景评估器已作为 `ScenarioMatch` 输入接入 runtime analysis path（显式 fixture context 优先、缺失时由 `scene-knowledge-v0.1` 确定性推导、`unknown` 不覆盖），见 [B5-1 场景知识库](b5-1-scene-knowledge.md)；B5-3 `SCENE-*` 推断节点仍不物化，属后续可选扩展，不计入阶段 5 缺口。

> B5-1 进展（成员 B，2026-10-07）：建立 `scene-knowledge-v0.1` 与确定性 `SceneConsistencyEvaluator`；无明确知识时 `UNKNOWN`，不从包名/domain/tracker/risk 反推场景；与 frozen `e-20260921-0001`～`e-20260921-0004` scenario context 一致。

> B5-1 runtime 接入（成员 B，2026-10-09）：`SceneConsistencyEvaluator` 经 `RuntimeFixtureLoader.loadSceneKnowledge` 加载 `scene-knowledge-v0.1` 并注入 `FixtureEventAnalysisService`；`scenarioMatch` 解析为「显式 fixture context 优先，缺失时由场景知识确定性推导，`UNKNOWN` 不覆盖」，行为与既有 frozen 评测保持一致，新增集成测试覆盖无显式 context 时的推导。

> B5-2 进展（成员 B，2026-10-07）：`EvidenceChainBuilder` 仅物化 effective matched rule 的 supporting event；`RiskAssessment.evidenceIds` 从主事件扩展到实际 supporting event；temporal/rule link 均明确为 E3 Derived Inference，不将时间相关表述为因果。

> B5-3 进展（成员 B，2026-10-07）：建立 deterministic causal/explanation DAG；事件节点保留 E1/E2/E4/E5，temporal/rule inference 为 E3，unknown assessment 为 E5；`supports` 不表述为 `causes`。

> B5-4 进展（成员 B，2026-10-08）：建立 deterministic `RecommendationSelector`；unknown/degraded 不生成执行请求；权限/后台活动建议映射 `OPEN_SETTINGS`；后台网络仅在 `evidenceIds` 中存在可靠 domain 时映射 `BLOCK_DOMAIN`；P0 不生成 `BLOCK_APP`。

> B5-5 进展（成员 B，2026-10-08）：建立 deterministic `RecheckComparator`；基于等长前后窗口比较 `allowedCount`，区分 `NO_REQUEST` 与 `ALL_BLOCKED`，输出 `reduced/no_change/blocked/unknown`；非 executed、`OPEN_SETTINGS`、坏快照或不可比窗口均诚实降级为 `unknown`。

> B5-6 进展（成员 B，2026-10-08）：Event Detail 已接入因果链、canonical Recommendation、处置与复查状态；`BLOCK_DOMAIN` 执行后基于固定 observation window 复查，`observationEnd` 前禁止查询 post window；`RecheckComparator` 结果可持久化并恢复；`OPEN_SETTINGS` 不伪装为已修改权限，`executed` 与 verified effect 保持分离。完整 app/rule-engine tests、assemble 和 lint 已通过。

> B5-7 进展（成员 B，2026-10-09）：建立处置前后评测集 `fixtures/recheck-cases-v0.1.json`（输入：`MitigationRecord` + 处置前后 `NetworkObservation`）与 `fixtures/recheck-expected-v0.1.json`（oracle：`reduced/no_change/blocked/unknown`），共 12 条，回归入口 `RecheckEvaluationDatasetTest`；输入与 oracle 严格隔离，覆盖四类结论及非 executed、非 `block_domain`、坏快照、不可比窗口等降级边界，`reduced/no_change/blocked/unknown` 四类均有样例且比较确定。详见 [B5-7 处置前后评测样例](b5-7-recheck-evaluation.md)。

> **阶段 5 执行层进展（A 回填，2026-10-03）**：A5-1~A5-6 代码完成，`:app` 单测 81 项通过（新增 `DeviceMitigationExecutorTest` 9 项、`RoomNetworkObservationRepositoryTest` 3 项）。契约新增 `core-model/.../MitigationApi.kt`（`MitigationAction`/`MitigationStatus`/`MitigationRequest`/`MitigationExecution`/`MitigationExecutor`/`NetworkObservation`/`NetworkRequestPresence`/`NetworkObservationRepository`）。实现：
> - **A5-1**：`mitigation/DeviceMitigationExecutor` 按 action 分派；`DomainBlockController` 经 ordered broadcast（`TrackerControlBroadcast.ACTION_BLOCK_DOMAIN`）请求底座阻断，仅 `RESULT_OK` 回执为 `CONFIRMED`，超时/未接线一律 `UNAVAILABLE`，绝不谎报。
> - **A5-2**：P0 只保证 `BLOCK_DOMAIN`；`BLOCK_APP` 明确 `UNSUPPORTED` 待 P1；`NONE` 无动作；缺域名目标亦 `UNSUPPORTED`。
> - **A5-3**：`mitigation/AndroidAppSettingsLauncher` 经 `ACTION_APPLICATION_DETAILS_SETTINGS` 跳转系统应用详情页，不声称“已生效”。
> - **A5-4**：仅 `BLOCK_DOMAIN`/`OPEN_SETTINGS` 落 `MitigationRecord`，写入处置前窗口快照（`preSnapshot`，`NetworkObservation` JSON）与 `observationEnd=now+window`（默认 5 分钟），`postResult` 初始 `unknown`；`MitigationRepository.updateOutcome` 供 B 复查回填。
> - **A5-5**：`RoomNetworkObservationRepository` 基于 `network_event` 按 App/域名/时间窗聚合连接数与阻断数。
> - **A5-6**：`NetworkObservation.presence` 区分 `NO_REQUEST`/`ALL_BLOCKED`/`SOME_BLOCKED`/`ALLOWED`。
>
> 已接入 `AppContainer`/`AppDependencies`（`networkObservationRepository`、`mitigationExecutor`）。**底座侧接收器补丁已完成并真机验证（2026-10-05；signature 权限安全验证 2026-10-07）**：`third_party/patches/a5-1-domain-block-receiver.patch` 新增 `eu.faircode.netguard.CausalGuardDomainBlockReceiver`（跨进程 ordered broadcast 处理 `ACTION_BLOCK_DOMAIN`，写 `mapCausalGuardBlocked` 并 `RESULT_OK` 回执）+ `ServiceSinkhole.isDomainBlocked` 仅按 CausalGuard 下发域名做 DNS 层抑制 + `AndroidManifest.xml` 注册导出 receiver，并以 **signature 权限**（`${applicationId}.permission.CAUSALGUARD_BLOCK_DOMAIN`）保护接收器——这是控制通道的唯一安全边界（签名权限本身已足以约束投递方，不再用 `Binder.getCallingUid()` 做二次 sender 校验）；App 侧 manifest `uses-permission` 声明该权限，发送时**不**再作为 `receiverPermission` 参数传入（实测 PJW110/Android 16 上传入会误拒授权 sender），安全边界由接收器 manifest `android:permission`（signature）强制。真机验证：两 APK signer SHA-256 一致（`77768f…`，同签）；授权 CausalGuard sender → `RESULT_OK`/`CONFIRMED`；未授权 `adb shell` sender → `Enqueued broadcast : 0`（receiver 未执行、未改 block set）。已纳入 `scripts/apply-trackercontrol-hook.sh`（A4-3 + A5-1 一并应用）并登记 `THIRD_PARTY_NOTICES.md`。两补丁 `git apply --check` 通过并应用到固定 commit `9504d41b` 的 submodule。**持久化语义修正（A5-4）**：`MitigationRecord` 新增 `executionStatus`（executed/unavailable/unsupported/failed/unknown，DB 升 v3 `MIGRATION_2_3`），执行器先执行动作再落库，失败/不可用显式标为非 executed，绝不伪装成已执行。真机 PJW110/Android 16 验证：`BLOCK_DOMAIN` 有效域名回执 `result=-1`（`RESULT_OK`，日志 `confirmed`）、缺域名回执 `result=0`（`RESULT_CANCELED`，日志 `missing domain, cancel`）；`:app` 单测通过。详见 `docs/spike-results.md` §3 A5-1。

## 阶段 6：AI 解释、评测与可用性（10/4-10/5）

| 编号 | 任务 | 主责 | 协作 | 状态 |
|---|---|---|---|---|
| A6-1 | Retrofit/OkHttp 安全配置、超时、无 Body 日志 | 成员 A | - | 完成 |
| A6-2 | 密钥通过本地配置/环境注入，仓库不得出现密钥 | 成员 A | - | 完成 |
| A6-3 | 长时间 VPN、网络切换、服务回收和真机稳定性测试 | 成员 A | - | 完成（有限时长一轮，长稳遗留） |
| A6-4 | 修复 P0 缺陷，不新增功能 | 成员 A | - | 完成（无阻断性缺陷） |
| B6-1 | 本地解释模板和失败兜底 | 成员 B | - | 已完成 |
| B6-2 | AI 输入白名单、脱敏 DTO、输出 schema 和事实校验 | 成员 B | 成员 A | 已完成 |
| B6-3 | 可选 `AiExplanationProvider` | 成员 B | 成员 A | 已完成 |
| B6-4 | 扩充到 30~50 条评测样例 | 成员 B | - | 已完成 |
| B6-5 | 统计规则准确率/召回率、事实一致率和处置耗时 | 成员 B | - | 已完成 |
| B6-6 | 保存误报、漏报和 unknown 案例并校准规则 | 成员 B | 成员 A | 已完成 |

> B6-4 进展（2026-10-09）：原 25 条 `rules-v0.1` historical regression 保持不变，新增 17 条 `rules-v0.2` current-runtime boundary cases，主规则评测资产共 42 条。新增独立 boundary 三件套：`evaluation-boundary-events-v0.1.json`、`evaluation-boundary-context-v0.1.json`、`evaluation-boundary-expected-v0.1.json`；覆盖 R-003 独立正例、R-006 正/负例、R-007 59,999/60,000/60,001 ms、R-008 v0.2 known UID + unknown package、R-009 equal timestamp/non-revoked prior、R-010 E5 完整 attribution 和 explicit scenario unknown。`:rule-engine:testDebugUnitTest` 已通过；不跨 ruleVersion 合并指标，分版本统计留给 B6-5。

> B6-5 进展（2026-10-09）：规则评测按版本报告 deterministic oracle agreement：`rules-v0.1` historical 25 cases 的 category/risk/scenario exact-match 均为 25/25，high-risk severity recall 为 9/9；`rules-v0.2` current boundary 17 cases 的 category/risk/scenario exact-match 均为 17/17，high-risk severity recall 为 6/6，`necessary` support 为 0、recall 为 `N/A`。这些是固定 curated evaluation set 上 RuleEvaluator 与项目定义 oracle 的一致性结果，不是现实 Android 环境泛化准确率。新增 12 条 synthetic AI explanation validation cases，`ACCEPT=4`、`SANITIZE=1`、`REJECT=7`，validator disposition 与 oracle 一致；real AI fact consistency 为 `N/A`，当前没有固定真实模型输出快照或独立人工事实标签。12 条 synthetic recheck cases 的 outcome 为 `reduced=3`、`no_change=2`、`blocked=1`、`unknown=6`，其中 6 条 confirmed case 用于 allowedCount 前后变化，unknown 不进入频率统计。user mitigation duration 为 `N/A`；observation window/recheck wait time 不冒充用户实际操作耗时，后续真人/真机测量 protocol 已记录在 [B6-5 指标文档](b6-5-evaluation-metrics.md)。`:rule-engine:testDebugUnitTest` 已通过。

> B6-6 进展（2026-10-09）：新增 `evaluation-failure-cases-v0.1.json` failure registry 与 test-only `FailureCaseAnalysisTest`，动态分析 42 条规则样例并区分 `false_positive`、`false_negative`、`classification_disagreement`、`unknown_boundary`、`context_only_unknown`。当前 registry 记录 0/0/0、8 条 explicit unknown boundary 和 6 条 context-only unknown；未知不自动视为漏报。R-006 的 `lastUsedAgoMs` 是 semantic/contract gap，R-008 已按 v0.2 package attribution 语义校准文档，R-009 无时间窗口属于后续 calibration candidate；本轮 production calibration decision 为 `NO_CHANGE`，未修改规则。`:rule-engine:testDebugUnitTest` 已在正常 WSL BUILD SUCCESSFUL。

阶段门：完全断网仍能完成主演示；AI 不达标时关闭在线 Provider，不影响 P0。

> B6-1 进展（成员 B，2026-10-09）：建立纯确定性、离线的 `LocalExplanationProvider`，按 [docs/11](11-ai-explanation-contract.md) §5 五段渲染，场景无法确认时诚实回退 `当前证据不足以确认风险。`；接入 `FixtureEventAnalysisService`（事件专属模板优先、缺失走本地模板），`EventAnalysisResult.explanation` 统一为 `ExplanationText`。断网/未配置/失败均可用。详见 [B6-1](b6-1-local-explanation.md)。

> B6-2 进展（成员 B，2026-10-09）：建立 `AiExplanationRequest.from()` 白名单投影（严格 12 字段，丢弃包名/域名/证据原文等本地字段）与 `ExplanationFactValidator` 本地事实校验（数字/App/事件类型/风险升级 → 丢弃回退；违规措辞 → 中性替换）。回归测试断言白名单无泄漏、各违规类均被拦截。详见 [B6-2](b6-2-ai-explanation-validation.md)。

> B6-3 进展（成员 B，2026-10-09）：建立 `AiExplanationProvider` 契约（纯 Kotlin，只发白名单、返回原始 `ExplanationText`）与确定性编排 `ExplanationService`：始终先渲染本地兜底，未配置/超时/异常 → `UNAVAILABLE`/`FAILED`，输出未过 B6-2 校验 → `REJECTED`，全部回退本地模板，`CancellationException` 原样抛出。App 侧 `RetrofitAiExplanationProvider`（`POST {AI_BASE_URL}/v1/explain`）仅在密钥注入时构造，接入 `FixtureEventAnalysisService`（事件专属模板仍优先）；`SettingsViewModel` 按 `aiExplanationAvailable` 诚实展示“AI 云端解释”是否配置。详见 [B6-3](b6-3-ai-explanation-provider.md)。

## 阶段 7：发布候选与完整演示（10/6-10/7）

| 编号 | 任务 | 主责 | 协作 | 状态 |
|---|---|---|---|---|
| A7-1 | 冻结设备、系统和构建环境 | 成员 A | 成员 B | 已完成（[A7 RC 记录](a7-release-candidate.md) 第 1 节：PJW110/Android 16/API 36；JDK 17.0.13+11、Gradle 9.6.1、AGP 9.4.1、compileSdk 37、底座 submodule `9504d41b`） |
| A7-2 | 生成 RC APK、commit SHA 和 SHA-256 | 成员 A | - | 已完成（`causalguard-0.2.0-rc1.apk`，SHA-256 `36d15cf5…`，签名证书 SHA-256 `b3ed3f7d…`；见 [A7 RC 记录](a7-release-candidate.md) 第 2 节） |
| A7-3 | 编写安装、授权、清理和故障恢复步骤 | 成员 A | 成员 B | 已完成（[A7 安装·授权·清理·故障恢复](a7-install-auth-recovery.md)） |
| A7-4 | 生成依赖许可证报告并人工核对 | 成员 A | 成员 B | 已完成（[A7 依赖许可证报告](a7-license-report.md)；脚本 `scripts/generate-license-report.sh`） |
| A7-5 | 准备真实网络数据来源、日志和第三方修改证据 | 成员 A | - | 已完成（[A7 RC 记录](a7-release-candidate.md) 第 5 节：底座 commit、两补丁 SHA-256、复现命令） |
| A7-6 | 只修阻断性缺陷 | 成员 A | - | 已完成（`assembleRelease` 通过；单测 261/0；lint 0 error/4 warning 均非阻断；无需改码） |
| B7-1 | 完成 Demo App 场景重置流程 | 成员 B | 成员 A | 已完成 |
| B7-2 | 3 分钟演示脚本和离线回放包 | 成员 B | 成员 A | 已完成 |
| B7-3 | 录制候选视频、生成核心截图 | 成员 B | - | 未开始 |
| B7-4 | 产品创新、证据等级、开源/原创边界答辩页 | 成员 B | 成员 A | 已完成 |
| B7-5 | 独立按 A 的说明安装、授权和运行 RC | 成员 B | - | 未开始 |

> 阶段 7 A 侧进展（成员 A，2026-10-07）：A7-1～A7-6 已完成，已推送分支 `release/v0.2.0-rc1` 并发起 **PR #27（待 CI + 成员 B review）**。冻结设备 PJW110/Android 16/API 36 与构建环境；构建并签名 RC `causalguard-0.2.0-rc1.apk`（SHA-256 `f1df27fb…`，源码构建 commit `c603d26`，tag `causalguard-v0.2.0-rc1`）；输出安装/授权/清理/恢复文档与依赖许可证报告（自动收集 + 人工核对）；整理底座 commit、两补丁 SHA-256 与复现命令作为第三方修改证据；回归 `assembleRelease` 通过、单测 261/0、lint 0 error/4 warning（均非阻断），无需修码。详见 [A7 RC 记录](a7-release-candidate.md)。
>
> 阶段 7 B 侧进展：B7-1 已完成。现有 reset 流程统一返回 `Ready`；DEMO-C reset 会作废 active token，旧异步 completion 不覆盖 `Ready`，重新 Arm 使用新 token；DEMO-D reset 清除 controller baseline，Activity Reset 同时清除 `DemoDSessionStore`。相关 unit tests 已覆盖这些 reset 边界。B7-2 已完成：3 分钟录屏脚本、offline replay README/manifest 已形成，复用已有 canonical fixture/runtime assets，未新增业务代码，并明确分离 synthetic replay 与真实 VPN/system observation；完成定义不包含 clean-install 一键 fixture replay UI。B7-4 已完成：已形成可直接用于答辩/PPT 的核心页素材，统一产品创新、证据等级、第三方/原创边界，复用 B8-3 与 B6-5/B6-6 事实，不重复生成阶段 8 长文，未新增业务代码。B7-3、B7-5 仍待材料制作与人工验收。

交叉验收：截图、视频、文档和 APK 来自同一 commit；授权失败、断网、VPN 停止和无法归属均完成演练。

## 阶段 8：最终提交准备（10/8-10/10）

| 编号 | 任务 | 主责 | 协作 | 状态 |
|---|---|---|---|---|
| A8-1 | 最终设备回归 | 成员 A | 成员 B | 未开始 |
| A8-2 | release APK、源码包、构建说明和依赖版本 | 成员 A | - | 未开始 |
| A8-3 | 清理密钥、账号、真实数据、原始日志和临时文件 | 成员 A | 成员 B | 未开始 |
| A8-4 | 核对 GPL 对应源码、许可证与 tag | 成员 A | 成员 B | 未开始 |
| A8-5 | 在 `main` 创建最终版本 tag | 成员 A | 成员 B | 未开始 |
| B8-1 | 正式设计文档 PDF | 成员 B | - | 未开始 |
| B8-2 | 3 张核心截图和最终 MP4 | 成员 B | - | 未开始 |
| B8-3 | 第三方与原创边界说明 | 成员 B | 成员 A | 已完成 |
| B8-4 | 评测结果和失败案例附录 | 成员 B | - | 进行中 |
| B8-5 | 核对所有文案、截图和视频不夸大实际能力 | 成员 B | 成员 A | 未开始 |


> 阶段 8 B 侧进展：B8-3 已完成，证据为 [第三方与原创边界说明](b8-3-third-party-original-boundary.md)；该任务不替代 A8-4 的 GPL/submodule 最终核验。B8-4 已具备核心来源：[B6-5 评测指标](b6-5-evaluation-metrics.md)、[B6-6 失败分析](b6-6-failure-analysis.md) 和 [评测失败案例登记](fixtures/evaluation-failure-cases-v0.1.json)，剩余为最终材料汇编，不需要新增评测代码。

最终交叉检查：干净环境按 README 编译源码（B）；按最终脚本完整演示 APK（A）；检查技术陈述与能力边界（A）；检查 APK/文档/视频/截图版本一致（B）；许可证和署名两人各检查一次。任何材料与最终 tag 功能不一致，不能提交。

## 范围缩减触发条件

| 时间点 | 触发条件 | 立即处理 |
|---|---|---|
| 9/22 晚 | TrackerControl 不能稳定构建/联网 | 换固定旧 tag；仍失败则缩小网络范围 |
| 9/27 晚 | 模拟闭环未跑通 | 删除在线 AI、PDF、图表和次要页面 |
| 9/30 晚 | 域名/UID 不稳定 | 展示 IP/unknown；主演示使用 Demo 真值 |
| 10/3 晚 | 阻断不稳定 | 仅保留单域名阻断，不做 App 级阻断 |
| 10/5 晚 | AI 事实一致性不足 | 关闭在线 AI，只保留本地模板 |
| RC 生成后 | 非阻断性新需求 | 进入 backlog，不加入本次版本 |

## Definition of Done

- [ ] 在正确的短任务分支，不是直接修改 `main`；
- [ ] 开始前已 fetch 并基于最新 `origin/main`；
- [ ] 有唯一主责、明确输入、输出和验收证据；
- [ ] 受影响测试通过；
- [ ] 新增第三方代码/数据已固定版本并登记许可证；
- [ ] unknown、权限拒绝、断网和缺失数据有降级；
- [ ] PR 已由另一人 review，CI 通过；
- [ ] 合并后 `main` 可编译且主演示链未破坏。
