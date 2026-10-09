# B8-3 第三方与原创边界说明

> 项目：CausalGuard（隐私因果哨兵）
> 任务：B8-3 第三方与原创边界说明
> 事实截止点：2026-10-09，父仓库 `HEAD=90da400` 及其已登记文件
> 核验范围：父仓库 gitlink、已提交登记文档、补丁文件、生成脚本和应用资产
> 限制：本 worktree 未初始化 `third_party/tracker-control-android`，未读取 submodule 工作树内容，未应用或回滚补丁。

## 1. 目的与适用范围

本文用于说明 CausalGuard 中第三方代码、第三方数据、平台/库依赖与团队原创实现之间的边界，服务于设计文档、答辩材料、许可证审计和最终提交材料。

本文只记录当前仓库能够确认的登记事实。外部调研文档中的“建议复用”不等同于当前仓库已接入；未初始化 submodule 内的许可证和源码范围不在本文中宣称为已完成核验。

本任务未新增开源代码、第三方依赖、第三方数据或第三方模板；
本任务仅整理仓库已有登记事实，且不替代 A8-4 的 submodule 内容核验。

## 2. 核验状态与证据边界

### 2.1 已核验

- 父仓库的 `third_party/tracker-control-android` gitlink 固定到
  `9504d41b9f6fa1509d784e5503c084d4b428307d`；登记文件同时记录 tag `2026080501`。
- 父仓库存在两处团队补丁文件：
  `third_party/patches/a4-3-serversinkhole-network-hook.patch` 和
  `third_party/patches/a5-1-domain-block-receiver.patch`。
- `THIRD_PARTY_NOTICES.md`、A7 许可证报告、A7 RC 记录和安装文档均登记了 TrackerControl、补丁、Disconnect 数据及其用途边界。
- `scripts/generate-tracker-dataset.py` 和
  `app/src/main/assets/tracker-domains-v0.1.json` 已提交在父仓库中；资产元数据记录了来源、快照 commit、数据许可证、输入资产路径和生成脚本。
- 本文没有引用 B6-3 未提交修改，也没有把 B6-4 或 A6-4 的工作写成已完成事实。

### 2.2 当前无法直接核验、保留给 A8-4

本 worktree 中 submodule 状态为未初始化（`git submodule status` 对该路径显示前导 `-`）。因此以下内容不能在本任务中写成“已核验完成”：

1. 固定 commit 内实际 `LICENSE` 文件的内容及文件头声明；
2. 固定 commit 内实际包含的源码范围；
3. 两处补丁是否能在该 commit 的工作树上完整应用；
4. 应用补丁后的完整对应源码如何随发布包提供；
5. 最终发布包中的第三方源码、补丁、许可证和最终 tag 是否逐项对应。

本文引用这些事项时，使用“登记记录”“父仓库 gitlink”或“待 A8-4 核验”的表述，不替代实际 submodule 内容检查。

## 3. 第三方代码

### 3.1 TrackerControl Android 网络底座

| 项目 | 当前仓库可确认的事实 |
|---|---|
| 官方来源 | [TrackerControl/tracker-control-android](https://github.com/TrackerControl/tracker-control-android) |
| 固定版本 | tag `2026080501`；gitlink commit `9504d41b9f6fa1509d784e5503c084d4b428307d` |
| 登记许可证 | GPL-3.0；登记文件同时提示底座内部部分组件/数据可能有独立许可 |
| 父仓库路径 | `third_party/tracker-control-android/`；由 `.gitmodules` 指向官方仓库 |
| 登记用途 | `VpnService`/TUN、TCP/UDP 处理、DNS 观测、连接记录和域名阻断网络底座 |
| 当前核验状态 | 父仓库 gitlink 和登记结论已核对；submodule 工作树未初始化，根 `LICENSE` 和实际源码范围待 A8-4 核验 |

TrackerControl/NetGuard 的底层网络能力不属于 CausalGuard 原创。CausalGuard 使用的是已登记的网络底座与跨进程适配边界；不能将 VPN/TUN 转发、协议处理、DNS 底层处理或底座阻断机制写成团队从零实现。

### 3.2 已存在的团队补丁与适配范围

父仓库当前提交了补丁文件，但本任务没有初始化 submodule，也没有重新应用或验证补丁。根据补丁文件、登记文件和 A7 记录，范围如下：

| 文件 | 登记的用途 | 登记的修改边界 | 当前状态 |
|---|---|---|---|
| `third_party/patches/a4-3-serversinkhole-network-hook.patch` | A4-3 网络事件广播桥接 | 新增 `CausalGuardNetworkHook`，在 `ServiceSinkhole` 的 `logPacket`/`dnsResolved` 回调挂接；发送脱敏网络元数据，不读取通信内容 | 补丁文件存在；应用状态和目标 commit 上的完整结果待 A8-4 核验 |
| `third_party/patches/a5-1-domain-block-receiver.patch` | A5-1 域名阻断控制通道 | 新增 `CausalGuardDomainBlockReceiver`、运行期阻断集、DNS 层抑制和 signature 权限保护；不修改 native 核心 | 补丁文件存在；应用状态和目标 commit 上的完整结果待 A8-4 核验 |
| `scripts/apply-trackercontrol-hook.sh` | 应用或回滚上述两处补丁 | 以固定底座源码为目标，支持应用和 `--revert` | 脚本文件存在；本任务未执行 |

A7 记录给出的补丁文件 SHA-256 为：

- A4-3：`97dec53c117a36da615672068e5aacc6e31570200d442bce02046bd463fbe024`
- A5-1：`28b7ef52d68ae25c2a5496d839fbe139e0e607c21cc45b5c60d58d22e329d3ad`
- 应用脚本：`6d259922f6d072e4786983c419192a8b1b15d7ffd64ea6b94f77c3ab068da5b3`

这些哈希是当前仓库 A7 记录和文件核对结果，不等同于本任务已完成 GPL 对应源码核验。

### 3.3 NetGuard 的表述边界

仓库登记和复用指南将 NetGuard 记录为 TrackerControl 的上游参考/底层来源，并未在父仓库中登记第二份独立 NetGuard submodule。本说明不把 NetGuard 写成单独新增接入项，也不把其底层能力写成团队原创。

## 4. 第三方数据

### 4.1 Disconnect Tracking Protection

| 项目 | 当前仓库可确认的事实 |
|---|---|
| 官方来源 | [disconnectme/disconnect-tracking-protection](https://github.com/disconnectme/disconnect-tracking-protection) |
| 上游数据 | `services.json`；当前应用资产的快照来源登记为 TrackerControl Android bundled asset |
| 快照关联 | TrackerControl commit `9504d41b9f6fa1509d784e5503c084d4b428307d` |
| 快照 blob SHA | `6fa1d74b3dd74a174fe1a90af5d2b59edd7865dc` |
| 数据许可证 | CC BY-NC-SA 4.0；登记为非商业使用，并保留 ShareAlike 约束 |
| 生成脚本 | `scripts/generate-tracker-dataset.py` |
| 应用资产 | `app/src/main/assets/tracker-domains-v0.1.json` |
| 资产内容 | `tracker-domains-v0.1`，生成 100 条精简域名记录，保留归一化域名、Disconnect 分类、来源标记和实体字段 |

脚本的已核对元数据包括：输入路径为
`third_party/tracker-control-android/app/src/main/assets/disconnect-blacklist.reversed.json`，输出路径为
`app/src/main/assets/tracker-domains-v0.1.json`，并在生成时检查 Disconnect 署名和 CC BY-NC-SA 4.0 文本。由于 submodule 未初始化，本文不宣称已经读取输入快照文件本身；这里只记录脚本和生成资产中已有的来源元数据。

### 4.2 数据与团队规则的边界

Disconnect 数据只提供公开列表中的域名、类别和实体分类。命中该数据只能说明域名被公开数据分类为某类服务，不能单独证明发生了隐私泄露、数据外传或恶意行为。

团队原创部分包括：

- 快照抽取、确定性筛选和输出结构的项目化处理；
- `TrackerClassifier` 和域名归一化的项目实现；
- 将网络事件、使用场景和权限状态进行关联；
- 风险规则、证据等级、解释和处置判断。

第三方数据的原始分类、来源实体和许可证不因被放入 CausalGuard 资产而变成团队原创，也不能与项目代码许可证混写。

## 5. 第三方库与平台依赖

以下是仓库 `THIRD_PARTY_NOTICES.md` 与 A7 许可证报告中已有登记的依赖摘要。它们不是本任务新增内容。

| 类别 | 已登记版本/范围 | 许可证结论 | 用途边界 |
|---|---|---|---|
| AndroidX Compose | Compose BOM `2026.09.00`；UI/Material3 等 | Apache-2.0 | UI 编译基础；CausalGuard 页面、状态和业务行为由团队实现 |
| AndroidX Activity / Navigation / Lifecycle | Activity Compose `1.13.0`；Navigation Compose `2.10.2`；Lifecycle Compose `2.11.0` | Apache-2.0 | Activity、导航和生命周期集成 |
| AndroidX Room / SQLite | Room `2.7.0`；SQLite `2.5.0` | Apache-2.0；SQLite 本体登记为公有领域 | 本地持久化和数据库访问 |
| Kotlin 与 kotlinx.serialization | Kotlin stdlib `2.2.10`；serialization `1.9.0` | Apache-2.0 | Kotlin 运行时和 JSON 契约/规则/评测资产解析 |
| Retrofit / OkHttp / Okio | Retrofit `3.0.0`；OkHttp `4.12.0`；Okio `3.6.0` | Apache-2.0 | 可选在线解释网络层；不改变本地确定性判断 |
| Android 测试与构建平台 | Android SDK、AGP `9.4.1`、Gradle `9.6.1`、NDK/CMake/Rust 等 | 以各自版本内 LICENSE/NOTICE、Android SDK 条款和 A7 报告为准 | 构建、测试和底座构建工具链 |

完整依赖清单以 [A7 依赖许可证报告](a7-license-report.md) 和 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) 为准。许可证报告明确区分了 release APK 的 Maven 运行时依赖、submodule、数据集和构建工具；自动报告不能替代人工核验。

## 6. 团队原创范围

在不把第三方底座或数据本身计入原创的前提下，仓库登记的团队原创范围包括：

- `PrivacyEvent`、`RiskAssessment`、`ExplanationResult` 等统一事件、风险和解释契约；
- UsageStats 与网络行为的时间关联、真实/演示/未知来源标签和降级语义；
- 场景知识库、场景一致性判断、风险规则内容、阈值和纯 Kotlin 规则引擎；
- E1–E5 证据等级、证据链、因果/解释结构和确定性本地解释；
- Recommendation、处置请求、处置前快照、观察窗口和复查比较逻辑；
- Compose UI、页面状态、解释文案和产品交互；
- Demo App 的 Ground Truth 场景、重置流程和演示状态机；
- fixture、评测输入、oracle、失败案例、指标材料和演示脚本；
- 将第三方网络底座事件转换为 CausalGuard 契约的适配层。

其中“适配层、数据抽取脚本和业务规则”是团队围绕第三方输入实现的原创工程，不等于拥有第三方网络底层、原始数据或其许可证。

## 7. 四种表述的区别

| 表述 | 本项目中的含义 | 对外应如何写 |
|---|---|---|
| 参考 | 阅读官方 sample、NetGuard 或调研项目以理解 API/架构 | “参考其设计/官方用法”；不能写成已复制或已接入 |
| 适配 | 在边界处把第三方输出转换为 CausalGuard 契约 | “团队实现了事件适配/广播桥接”；不能写成重写底座 |
| 实际接入 | 父仓库已有 gitlink、依赖登记、资产或补丁文件记录 | 必须同时给出来源、固定版本、许可证和实际路径 |
| 原创实现 | CausalGuard 的契约、规则、推理、解释、处置、UI、Demo 和评测 | 可归为团队实现，但不能覆盖第三方输入本身 |

在本任务中，“已接入”只用于父仓库已有登记事实；补丁在固定 commit 上的实际应用结果仍属于 A8-4 待核验事项。

## 8. 不能对外宣称为团队原创的内容

以下内容不能作为 CausalGuard 团队原创能力对外宣称：

- TrackerControl/NetGuard 的 VPN/TUN、TCP/UDP 转发、DNS 底层处理、连接记录和底座阻断能力；
- Disconnect 原始域名、实体和分类数据；
- Android、Kotlin、Compose、Room、Retrofit、OkHttp 等平台或通用库自身；
- 第三方库、底座源码、数据集和官方 sample 的原始实现。

允许宣称为团队工作的内容，是围绕这些输入完成的统一事件建模、场景关联、规则推理、证据分级、解释、处置复查、UI、Demo 和评测。

## 9. 许可证与发布责任

### 9.1 代码与库

- GPL-3.0 的 TrackerControl 底座及其衍生修改必须保留对应许可证、版权和修改记录，并按发布要求提供对应源码。
- A4-3/A5-1 团队修改以父仓库补丁形式登记；补丁应用后的底座源码是否与最终发布包对应，须由 A8-4 在初始化 submodule 后确认。
- Apache-2.0 等宽松库仍需保留相应版权和许可证信息；不能因为许可证宽松而从登记和发布材料中省略。

### 9.2 数据

- Disconnect 数据及其精简派生资产按 CC BY-NC-SA 4.0 记录，保留署名、非商业和 ShareAlike 约束。
- 数据许可与代码许可分开列示，不把 CC BY-NC-SA 4.0 写成项目代码许可证，也不把数据命中写成风险事实。

## 10. 与登记、A8-4 和最终提交材料的关系

### `THIRD_PARTY_NOTICES.md`

`THIRD_PARTY_NOTICES.md` 是第三方来源、版本、许可证、使用范围、修改内容和原创边界的主登记文件。本任务只引用它，不修改它；若 A8-4 发现具体 commit 内的许可证或源码范围与登记不一致，应由授权责任人先更新主登记，再同步最终材料。

### A8-4 GPL 核对

A8-4 仍需在允许初始化 submodule 的环境中：

1. 检查 gitlink 指向的完整 commit、tag、根 `LICENSE` 和相关文件头；
2. 检查两处补丁能否在该 commit 上应用，并确认补丁后的源码范围；
3. 确认 GPL 对应源码、补丁、许可证和发布路径；
4. 核对最终 tag、APK、源码包和许可证材料的一致性。

本文不替代以上工作，也不把 A7 文档中的历史记录升级为本任务已经完成的 submodule 核验。

### 最终提交材料

设计文档 PDF、源码包、APK、截图和 MP4 应对应同一最终版本/tag。文案必须同时说明真实 Android 观测、演示/fixture 数据和第三方网络底座边界；不能把演示数据写成真实系统事实，也不能把底座或公开 tracker 分类写成“团队自研网络监控”或“已证明发生泄露”。

## 11. 许可证风险与待办

| 风险/待办 | 当前结论 | 后续责任 |
|---|---|---|
| submodule 根 `LICENSE` 和文件头 | 当前 worktree 未初始化，未直接读取 | A8-4 |
| 固定 commit 的实际源码范围 | 仅确认父仓库 gitlink 和登记范围 | A8-4 |
| 补丁可应用性与应用后源码 | 当前仅核对补丁文件、脚本和 A7 登记哈希，未重新应用 | A8-4 |
| GPL 对应源码提供方式 | 登记文件已有计划，但本任务不作最终发布确认 | A8-4 / 发布责任人 |
| Disconnect 快照许可证 | 资产和脚本元数据记录 CC BY-NC-SA 4.0；输入快照本体未在本 worktree 读取 | A8-4 及许可证复核 |
| 非商业与 ShareAlike | 必须保留在最终数据说明和再分发材料中 | 成员 B 汇总、成员 A 复核 |
| Maven/平台依赖 | A7 报告已有自动收集与人工核对结论 | 发布前复核最终 tag |

## 12. 文案底线

推荐使用：

> CausalGuard 基于固定版本的 TrackerControl Android 网络底座获取网络元数据，并通过团队实现的事件契约、场景规则、证据链、解释、处置复查和 UI 完成隐私风险分析。TrackerControl/NetGuard 网络底层能力和 Disconnect 域名分类数据属于第三方；本文所述 GPL 对应源码和补丁最终以 A8-4 核验及最终发布材料为准。

避免使用：

- “我们自研了 VPN/TUN、TCP/IP 协议栈或 TrackerControl 网络底座”；
- “命中 tracker 列表就证明发生了隐私泄露”；
- “已经完成 GPL 源码核验”（在 A8-4 未完成前）；
- “参考过”与“已接入”混用；
- 将 Apache-2.0 库、GPL-3.0 代码和 CC BY-NC-SA 4.0 数据放在同一许可证结论中。

## 13. 事实来源

- [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)
- [20 开源代码复用调研与接入建议](20-open-source-reuse-guide.md)
- [12 隐私、安全和合规设计](12-privacy-security-design.md)
- [A7 依赖许可证报告](a7-license-report.md)
- [A7 发布候选记录](a7-release-candidate.md)
- [A7 安装、授权、清理与故障恢复](a7-install-auth-recovery.md)
- [15 验收清单](15-acceptance-checklist.md)
- [16 演示与发布方案](16-demo-and-release-plan.md)
- [17 任务看板](17-task-board.md)
- [18 风险清单](18-risk-register.md)
- [19 工作导引](19-work-guide.md)
- [Disconnect 数据生成脚本](../scripts/generate-tracker-dataset.py)
- [tracker-domains-v0.1.json](../app/src/main/assets/tracker-domains-v0.1.json)
- [A4-3 补丁](../third_party/patches/a4-3-serversinkhole-network-hook.patch)
- [A5-1 补丁](../third_party/patches/a5-1-domain-block-receiver.patch)
