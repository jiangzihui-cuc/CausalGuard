# CausalGuard（隐私因果哨兵）正式设计文档源稿

> 文档任务：B8-1（阶段 8：最终提交准备）
> 文档形态：最终设计文档 PDF 的 Markdown 源稿；本轮不生成 PDF
> 事实基线：本 worktree 基于 `origin/main`，HEAD 为 `54f37a983668dcb2621e810b738ca3a1d1303ce3`
> 事实截止点：以当前仓库文件为准；文中日期沿用各事实来源文档的日期
> 状态：源稿草案；截图、视频、最终评测汇编、最终设备回归和最终发布包仍需按阶段 8 计划补齐

## 目录

- [1. 文档目的与事实边界](#1-文档目的与事实边界)
- [2. 项目背景、问题定义与目标](#2-项目背景问题定义与目标)
- [3. MVP 范围、非目标与 Android 能力边界](#3-mvp-范围非目标与-android-能力边界)
- [4. 总体架构与模块边界](#4-总体架构与模块边界)
- [5. 端到端数据流](#5-端到端数据流)
- [6. 核心数据契约与字段责任](#6-核心数据契约与字段责任)
- [7. 规则引擎、解释与安全边界](#7-规则引擎解释与安全边界)
- [8. 真实数据、Demo/沙箱数据与 fixture](#8-真实数据demosandbox数据与-fixture)
- [9. 处置建议与复查闭环](#9-处置建议与复查闭环)
- [10. 测试、评测与失败案例](#10-测试评测与失败案例)
- [11. 隐私、安全、权限与日志](#11-隐私安全权限与日志)
- [12. 开源复用与团队原创边界](#12-开源复用与团队原创边界)
- [13. 当前状态、任务输入输出与依赖](#13-当前状态任务输入输出与依赖)
- [14. 最终演示与验收映射](#14-最终演示与验收映射)
- [15. 生成最终 PDF 前需补齐的材料](#15-生成最终-pdf-前需补齐的材料)
- [16. 事实来源](#16-事实来源)

## 1. 文档目的与事实边界

### 1.1 文档目的

本源稿用于汇总 CausalGuard 的需求、系统设计、数据契约、规则与解释、处置复查、隐私安全、测试评测和开源边界，作为后续正式设计文档 PDF 的内容源。它描述系统如何工作、哪些信息可以作为证据、哪些结论必须降级，以及当前仓库能够证明到什么程度。

本源稿不是最终发布承诺，也不是对未完成 A8/B8 材料的替代。文中“已完成”仅指当前 `origin/main` 及其已登记事实能够支持的结论；“待完成”“待最终核验”表示看板或发布材料中尚未完成的事项。

### 1.2 事实与表述规则

- Android 系统事实、授权观测、规则推断、演示真值和无法确认状态必须分开标注。
- 权限声明不等于权限刚刚被使用；拥有权限并建立网络连接也不等于发生了数据泄露。
- 无法归属、缺少域名、缺少场景或证据等级为 E5 时，系统保留 `unknown`，不强行归因。
- TrackerControl/NetGuard 提供的是第三方网络底座能力；团队原创内容位于事件契约、上下文关联、规则、证据解释、处置复查、UI、Demo 和评测等上层。
- 当前 submodule 未初始化，因此本稿不把固定 commit 内的源码范围、实际 `LICENSE` 文件内容和补丁可应用性写成已经完成的现场核验；这些事项由 A8-4 最终核对。

## 2. 项目背景、问题定义与目标

### 2.1 背景

普通 Android 用户可以看到权限、应用和部分网络信息，但很难回答三个连续问题：某个行为在什么使用场景下发生、现有证据支持怎样的风险判断、用户现在可以采取什么可逆行动并在行动后复查。单纯展示权限列表或 tracker 名称，不能形成可追溯的解释闭环。

CausalGuard 的定位是运行在 Android 设备上的隐私行为解释与处置智能体。它把 App 使用上下文、敏感行为信号、网络连接元数据和授权状态关联为结构化证据，再由确定性规则给出风险判断、解释边界、行动建议和复查结果。在线 AI 只属于可选解释增强，核心分析和离线模板不依赖在线服务。

### 2.2 问题定义

给定一组来自 Android 官方能力、用户授权网络观测或受控 Demo 的事件，以及 App 画像、使用上下文和规则版本，系统需要：

1. 保留事件的来源、时间、证据等级和真实/演示属性；
2. 判断行为与场景是否匹配，并区分事实与规则推断；
3. 在证据不足、无法归属或能力不可用时输出 `unknown`，而不是生成确定性指控；
4. 把风险结果转化为用户可执行、可逆、可审计的建议；
5. 对支持比较的网络处置记录观察窗口，给出“减少、无变化、被阻断或无法确认”的复查结论。

### 2.3 项目目标

- 建立一条可解释的“采集行为证据 → 场景判断 → 因果/证据解释 → 处置 → 复查”闭环。
- 用固定的 `PrivacyEvent`、`RuleInput`、`RiskAssessment` 和解释结果连接采集、规则和 UI，减少模块间隐式推断。
- 让模拟数据可以独立驱动规则、解释和页面，使测试不依赖 VPN 或在线服务。
- 在可获得的真实网络元数据范围内提供诚实观测，并对 Android 系统不可观测能力给出明确降级。
- 将第三方网络基础设施与团队原创的隐私分析价值清晰分界，支持后续许可证和最终材料核对。

## 3. MVP 范围、非目标与 Android 能力边界

### 3.1 MVP 范围

P0 围绕“后台敏感行为/位置相关场景与网络行为解释”主案例，包含：

| 能力 | MVP 处理方式 | 主要证据或输出 |
|---|---|---|
| 首次授权引导 | 说明用途、后果和停止监测方式；逐项处理通知、Usage Access、VPN | 授权状态与降级提示 |
| App 能力画像 | 读取系统可见 App、UID、版本、声明权限和当前授权状态 | `AppProfile` |
| 使用上下文 | 使用 `UsageStatsManager` 获取前后台/近期状态；缺授权时降级 | `foregroundState` |
| 网络观测 | 通过 VPN 底座接收 TCP/UDP/DNS 连接元数据和归属线索 | `NetworkEvent`/`PrivacyEvent` |
| 统一事件时间线 | 事件入库、去重、聚合、详情和证据展开 | `PrivacyEvent` |
| 本地规则 | 运行 5～10 条 P0 规则及当前版本资产 | `RiskAssessment` |
| 解释 | 证据卡片、确定性本地模板；在线 Provider 可选 | `ExplanationText`/解释结果 |
| 处置与复查 | 域名阻断或系统设置跳转；记录执行状态和观察窗口 | `MitigationRecord`/复查结论 |
| Demo 沙箱 | 由自研 Demo App 提供四类可重复场景和真值 | `isDemo=true` 的演示状态或事件 |

### 3.2 非目标

首版明确不做以下事情：

- TLS 中间人解密、读取 HTTPS 请求体或聊天内容；
- 通过无障碍服务监控其他 App 页面；
- 承诺获取其他 App 每一次定位、相机、麦克风、通讯录或剪贴板调用历史；
- 依赖 `AppOpsManager` 获取其他 App 的全局敏感 API 访问历史；
- 自动撤销其他 App 权限；
- 从零实现完整 TCP/IP 协议栈；
- 让大模型直接读取原始网络流量并独立判定泄露；
- 在没有证据时使用“窃取”“恶意上传”“已泄露”等确定性措辞；
- 在首版同时适配多个移动平台。

### 3.3 Android 能力边界

项目基线为 Android 10/API 29+。能力按证据等级和可观测性处理：

| 能力 | 能确认的事实 | 不能声称的内容 | 缺失时的降级 |
|---|---|---|---|
| `PackageManager` | App 身份、声明权限、当前授权状态 | App 刚刚使用了某权限 | 只展示系统可见 App |
| `UsageStatsManager` | 前后台、近期使用等上下文（需授权） | 完整敏感 API 访问历史 | `foregroundState=unknown`，结论降级 |
| `VpnService`/底座 | 观测到的连接、协议、IP、端口、域名线索、阻断事实 | 请求体内容或数据泄露事实 | 只展示可获得的 IP/元数据 |
| UID 归属 | 底座路径下可获得的包名/UID 归属 | App 侧直接调用能稳定得到归属 | `packageName=unknown`，置信度降低 |
| 域名线索 | DNS 会话中可见的线索 | 加密 DNS 下必然可见的完整域名 | 保留 IP 或无法确认 |
| Demo App | 自身受控场景的真值或平台限制结果 | 将 Demo 结果冒充普通第三方 App 的系统观测 | 标注“演示数据”或“Platform Restricted” |

能力矩阵还记录了目标设备上的边界：Android 16/API 36 普通后台 App 的剪贴板 probe 可能被平台拒绝；该结果只代表对应设备验证，不扩大为所有 Android 版本的绝对结论。

## 4. 总体架构与模块边界

### 4.1 分层架构

```text
Android/System sources / Demo App / VPN adapter
                 │
                 ▼
        采集适配与统一事件库（Room/SQLite）
                 │
                 ▼
    场景知识 + 证据关联 + rule-engine（纯 Kotlin）
                 │
                 ▼
    风险评估 + 因果/证据解释 + 本地/在线解释
                 │
                 ▼
       Compose UI + 处置执行 + 复查比较
```

模块依赖方向为展示/应用编排 → 数据与洞察 → 公共契约；`core-model` 和规则引擎不反向依赖 Android 采集 API、Room 或 UI。页面消费聚合后的状态，不直接读取 Android 原始 API；规则只读事件，不修改原始事件。

### 4.2 模块边界

| 模块 | 责任 | 输入 | 输出 | 边界 |
|---|---|---|---|---|
| `core-model` | 暴露事件、枚举、规则、处置和 Repository/Adapter 契约 | 结构化数据 | `PrivacyEvent`、`RuleInput`、`RiskAssessment` 等 | 纯契约，不依赖 Android/Room/UI |
| `app` | 采集适配、Usage/画像、Room、分析编排、网络桥接、Compose UI、设置和执行入口 | Android API、底座广播、fixture、契约 | 入库事件、页面状态、执行记录 | 不把页面写成采集器；密钥不入库 |
| `rule-engine` | 场景一致性、规则匹配、证据链、因果链、推荐选择、复查比较、解释校验 | `RuleInput`、规则资产、场景资产 | `RiskAssessment`、推荐/请求、解释和复查结果 | 纯确定性逻辑，不执行设备动作 |
| `demo-app` | 提供受控场景、平台 probe、重置和真值/限制状态 | 演示操作和 Android 自身权限 | Demo 状态或标记为演示的事件 | 不代表主 App 能获得其他 App 的完整敏感历史 |
| TrackerControl/NetGuard 网络底座 | VPN/TUN、连接/DNS 元数据、网络阻断等第三方基础设施 | Android VPN 授权和网络流量 | 底座网络事件与阻断回执 | 第三方能力，不属于团队原创 |

### 4.3 当前实际工程结构

当前仓库已包含 `app/`、`core-model/`、`rule-engine/` 和 `demo-app/`。`settings.gradle` 当前纳入 `:app`、`:core-model`、`:demo-app`，并在 `rule-engine/` 存在时纳入 `:rule-engine`。`app` 内实际可见的边界包括：

- `data/`：Room、事件导入/入库、真实 Provider、网络事件源和 TrackerControl 广播桥；
- `analysis/`：fixture/runtime 分析编排、场景上下文、风险结果和解释结果汇总；
- `rules/`（由 `rule-engine` 提供）：规则资产、证据链、因果链、推荐和复查；
- `explain/`：可选 Retrofit/OkHttp Provider 与安全网络层；
- `mitigation/`：系统设置跳转、域名阻断请求和执行结果；
- `ui/`：首页、时间线、事件详情和设置页面；
- `demo-app/`：Map、Calculator、Weather flavor 与 Demo 场景控制器。

当前仓库还包含 50 个测试文件和 83 个主要 Kotlin 源文件（按本 worktree 文件清单统计）；这些数量用于说明工程已存在，不替代构建或真机验收结论。

## 5. 端到端数据流

系统的正式数据流为：

```text
Android/System sources
→ PrivacyEvent/RuleInput
→ rule-engine
→ RiskAssessment
→ ExplanationResult
→ UI/处置/复查
```

在实现中，真实网络分支的具体路径是：

```text
TrackerControl/NetGuard 底座或回放源
→ NetworkEventSource
→ NetworkEventIngestor
→ PrivacyEventRepository / Room
→ FixtureEventAnalysisService
→ RuleEvaluator.evaluate / assess
→ EvidenceChainBuilder + CausalChainBuilder
→ ExplanationService / 本地模板
→ Compose 事件详情
→ MitigationExecutor（若用户执行）
→ MitigationRecord + NetworkObservation
→ RecheckComparator
→ 复查结果与页面
```

关键转换和责任如下：

1. 采集适配器只产生来源、时间、网络元数据、使用状态或 Demo 标识；不把推断结果写成系统事实。
2. `NetworkEventIngestor` 将底座脱敏连接元数据转成 `PrivacyEvent(eventType=network)`，补齐 `schemaVersion`、`evidenceLevel=E2`、`source=vpn`、去重键和默认前后台状态。
3. 分析服务从事件库选出同 App 的 `relatedEvents` 和更早的 `priorEvents`，合并 App 画像、显式 fixture 场景上下文或场景知识推导结果，组成 `RuleInput`。
4. 规则引擎只读 `RuleInput`，按稳定顺序匹配规则并生成 canonical `RiskAssessment`、有效证据链、因果链和推荐选择。
5. 解释层只改写结构化结果的表达；本地模板是离线兜底，在线 Provider 的输入白名单和输出事实校验在本地执行。
6. 处置层只在请求具备可靠目标和执行能力时发起动作；观察层只提供连接计数事实，前后是否减少由复查比较器判断。

## 6. 核心数据契约与字段责任

### 6.1 `PrivacyEvent`

`PrivacyEvent` 是采集层到事件库的统一契约，当前 `schemaVersion` 为 `0.1`。关键字段责任为：

| 字段 | 责任 |
|---|---|
| `eventId`、`timestamp` | 事件身份和发生时间；入库保持幂等 |
| `appId`、`appName` | 应用标识；无法归属时使用 `unknown`，不从域名强行反推 |
| `eventType` | `clipboard`、`location`、`contacts`、`network`、`usage_context`、`permission` 或 `unknown` |
| `foregroundState` | 前台、后台、近期、未使用或 `unknown`；缺数据不能伪造 |
| `source`、`isDemo` | `system_api`、`usage_stats`、`vpn`、`demo`、`mock` 等来源，以及是否演示 |
| `evidenceLevel` | E1 系统事实、E2 授权观测、E3 规则推断、E4 演示真值、E5 无法确认 |
| `evidenceSummary` | 不含敏感原文的事实摘要、长度或必要哈希 |
| `network`/`usage` | 对应事件的附加结构；网络域名可为空、UID 可为 `-1` |
| `dedupKey` | 短时间窗口聚合键，不删除原始事件 |

剪贴板原文、通讯录内容、精确坐标、聊天内容和请求体不进入事件契约和本地留存。

### 6.2 `RuleInput` 与 `RiskAssessment`

`RuleInput` 由 `event`、可选 `appProfile`、可选 `usageContext`、上游 `scenarioMatch`、相关事件、历史事件和规则版本组成。规则输入上下文和 expected oracle 分离：评测期望字段不得写入正式事件。

`RiskAssessment` 的核心责任是记录：

- `riskScore`、`riskLevel`：风险输出，不等同于现实世界概率；
- `scenarioMatch`：`match`、`match_with_concern`、`mismatch` 或 `unknown`；
- `confidence`、`category`：置信度和 `necessary`/`analytics`/`high_risk`/`unknown` 分类；
- `explanationBoundary`：证据支持什么、不能支持什么；
- `evidenceIds`：主事件及实际 supporting event 的事件 ID，不等同于 `EvidenceLink.id`；
- `matchedRules`、`ruleVersion`：规则审计和复现依据；
- `createdAt=0`：确定性评估边界的未分配持久化时间，不可冒充真实评估时间。

### 6.3 解释、推荐、处置和复查契约

| 契约 | 产生方 | 消费方 | 重要约束 |
|---|---|---|---|
| `ExplanationText`/解释结果 | 本地模板或可选 AI 编排 | UI | 只能表达已验证的结构化事实 |
| `Recommendation` | 规则推荐选择器 | UI/处置请求 | 是建议和审计 DTO，不代表已执行 |
| `MitigationRequest` | 团队规则层 | `MitigationExecutor` | 只表达可执行请求计划 |
| `MitigationExecution`/`MitigationRecord` | 执行层 | 复查层和 UI | `executed`、不可用、失败、unsupported 必须分开 |
| `NetworkObservation` | 网络观测仓库 | `RecheckComparator` | 只报告请求/阻断计数，不自行判断减少 |
| `RecheckResult` | 复查比较器 | UI/持久化 | `reduced`、`no_change`、`blocked`、`unknown` |

## 7. 规则引擎、解释与安全边界

### 7.1 规则执行

当前运行时使用 `risk-rules-v0.2.json`；`risk-rules-v0.1.json` 保留为历史回归基线。规则资产、事件输入和 expected oracle 分离，规则只引用正式契约中的事件、App 画像和使用上下文字段。

规则引擎的确定性约束：

- 先按 priority 降序、同 priority 按规则 ID 升序建立稳定匹配顺序；
- 普通多规则命中保留全部有效规则；主规则由风险等级、置信度、priority 和稳定 tie-break 选择；
- `category=unknown` 的命中会抑制确定性高风险归因，输出低风险、`unknown` 场景、低置信度和 `none` 建议；
- 规则版本不匹配、无匹配或规则异常时，返回安全的最低置信度结果，不阻断事件入库和页面展示；
- 规则不修改原始事件，同一版本同一输入必须产生稳定输出。

P0 规则覆盖前台合理访问、后台敏感访问、场景不匹配后台联网、高频行为、已知分析追踪器、长期未使用仍联网、敏感行为伴随网络、无法归属网络、权限撤销后访问和证据不足等情形。当前规则资产的真实语义优先于概念描述：例如 R-006 在现有运行时按 `foregroundState=unused` 匹配，`lastUsedAgoMs` 阈值属于待后续校准的 semantic/contract gap。

### 7.2 unknown 降级与多规则合并

以下情况必须保持 unknown 或降低置信度：包名无法可靠归属、域名缺失且没有其他分类证据、证据等级为 E5、场景/使用上下文/先前事件不足、规则版本不匹配或平台能力本身不可观测。unknown 可以被展示、保留并纳入边界评测，但不生成确定性 App 处置请求，不声称发生数据泄露。

证据链中，事件节点保留原始 E1/E2/E4/E5；时间关联和规则关系属于 E3 derived inference；unknown 评估属于 E5。因果链中的边只表示 `supports`，不把时间相关性写成 `causes`。

### 7.3 解释边界

本地解释按“发生了什么、为什么关注、依据是什么、可以怎么做、不确定性”五段生成。在线解释只接收脱敏白名单字段，且本地验证：数字、应用、事件类型和风险级别必须与输入一致，违规或新增事实时丢弃并回退本地模板。在线 AI 关闭、未配置、超时、异常或输出未通过验证，都不影响 P0 核心路径。

## 8. 真实数据、Demo/沙箱数据与 fixture

### 8.1 三类输入边界

| 类型 | 来源 | 语义 | 展示要求 |
|---|---|---|---|
| 真实数据 | Android 官方 API、用户授权 Usage Access、VPN 底座元数据 | 设备上实际观测到的有限事实 | 标出观测来源和能力限制 |
| Demo/沙箱数据 | 团队控制的 Demo App 场景 | 受控操作的真值或平台限制结果 | `isDemo=true` 或明确“演示数据” |
| fixture/回放数据 | 仓库 JSON 资产、Fake Repository、离线回放 | 可重复的测试/演示输入，不等于设备实时事实 | 标出 fixture/mock/回放语义 |

Demo-A～D 的职责是提供前台位置、后台剪贴板 probe、后台最小网络连接尝试、撤权后位置 probe 等受控场景。平台拒绝或应用层失败也是结果的一部分；若没有生成真实 `PrivacyEvent`，不得包装成成功访问事件。

### 8.2 fixture 资产

当前仓库包含：

- 10 条冻结的 `privacy-events-v0.1` 事件及其 expected/context 旁路；
- 15 条 `rules-v0.1` historical evaluation extension；
- 17 条 `rules-v0.2` current-runtime boundary cases；
- 12 条处置前后复查样例；
- 12 条 synthetic AI explanation guardrail 样例；
- failure registry，记录 explicit unknown boundary 与 context-only unknown，不把 unknown 自动计为漏报。

这几类资产分别承载事件输入、上游上下文、规则输出 oracle、解释校验和复查 oracle，不能把 expected 字段写入正式 `PrivacyEvent`。

## 9. 处置建议与复查闭环

### 9.1 建议与执行分离

规则先生成 `Recommendation`。可靠包名和可靠域名证据存在时，后台网络建议可以映射为 `BLOCK_DOMAIN`；权限或后台活动建议映射为 `OPEN_SETTINGS`。P0 不生成 App 级阻断请求。缺域名、unknown、版本不匹配或证据不足时不生成确定性执行请求。

执行层由成员 A 的真实系统/底座能力承接，能够报告 `executed`、`unavailable`、`unsupported` 或 `failed`。打开系统设置页不代表用户已经修改权限；阻断回执也需要与后续观察结果分开记录。

### 9.2 复查流程

```text
RiskAssessment
  → Recommendation
  → MitigationRequest
  → 真实执行或诚实降级
  → 处置前快照 + observation window
  → 处置后 NetworkObservation
  → RecheckComparator
  → reduced / no_change / blocked / unknown
```

`RecheckComparator` 仅比较合法、等长、前后相邻的观察窗口和 `allowedCount`：允许连接数下降可判为 `reduced`，后窗口全部阻断可判为 `blocked`，相同则为 `no_change`；没有已执行动作、没有可靠基线、窗口不可比、快照损坏或无法确认时输出 `unknown`。观察窗口时长不冒充用户实际操作耗时。

## 10. 测试、评测与失败案例

### 10.1 测试层次

- `core-model`：JSON 契约、枚举未知值和模型序列化；
- `rule-engine`：规则命中、同输入稳定性、版本不匹配、多规则合并、证据链、因果链、推荐和复查；
- `app`：Room/DAO/迁移、事件导入、网络事件转换、Provider、广播桥、解释 Provider、ViewModel 和页面状态；
- `demo-app`：场景前置条件、平台限制、异步 probe token、重置和状态恢复；
- 集成/真机：VPN 启停、网络切换、UID 归属、域名线索、阻断回执、授权拒绝、断网和服务回收。

### 10.2 已有评测口径

仓库文档记录：`rules-v0.1` historical 25 cases 与 `rules-v0.2` current boundary 17 cases 分版本统计，不能合并成一个版本准确率。两组当前 curated oracle 的 category/risk/scenario exact-match 均为 25/25 和 17/17；high-risk severity recall 分别为 9/9 和 6/6。这些结果只表示固定评测集与项目 oracle 的一致性，不代表现实 Android 环境的泛化准确率。

AI 评测使用 synthetic candidate/oracle：记录为 `ACCEPT=4`、`SANITIZE=1`、`REJECT=7`，不能把它写成真实模型事实一致率；真实 AI fact consistency 当前为 `N/A`。复查 synthetic cases 记录 `reduced=3`、`no_change=2`、`blocked=1`、`unknown=6`，unknown 不进入已确认变化的频率统计；用户实际处置耗时当前为 `N/A`。

### 10.3 失败案例原则

失败 registry 区分 `false_positive`、`false_negative`、`classification_disagreement`、`unknown_boundary` 和 `context_only_unknown`。当前文档记录 0 个前述误报、漏报和分类不一致项，同时保留 8 个 explicit unknown boundary 和 6 个 context-only unknown。该数字是当前 curated 数据集状态，不是现实世界误报/漏报统计。

R-006 的 `lastUsedAgoMs` 语义缺口、R-009 缺少独立时间窗口等事项保留为后续校准候选；当前 production calibration decision 为 `NO_CHANGE`，不能为提高指标而修改规则。

## 11. 隐私、安全、权限与日志

### 11.1 权限与降级

主 App 按需处理通知、Usage Access、VPN 和前台服务等能力；拒绝或撤销权限不应导致闪退，而应显示待授权、监测暂停、使用上下文不可用或进入 Demo/离线模式。主 App 不申请定位、通讯录、相机、麦克风或剪贴板权限来伪造对其他 App 的敏感访问历史；Demo App 的自身能力与平台 probe 单独标记。

### 11.2 数据最小化

本地可以留存事件元数据、App 画像、风险结果、规则版本、处置记录和复查结果；不保存剪贴板原文、通讯录内容、精确位置轨迹、聊天内容或请求体。摘要只能使用必要的长度、哈希或中性描述。域名可脱敏展示，AI 请求只能发送白名单结构化字段。

### 11.3 日志与审计

审计日志可以记录 AI 模型名/版本、输入字段白名单、输出状态和时间，但不得记录敏感原文、完整请求体或密钥。运行日志用于排查 VPN、授权、阻断和回放状态，不得将原始设备日志、真实账号或密钥打包进最终提交材料。A8-3 仍需完成最终清理核对。

## 12. 开源复用与团队原创边界

### 12.1 本轮原则

本轮不新增开源代码、第三方依赖、第三方数据或第三方模板；仅引用仓库已有的接入和许可证事实。本源稿不复制第三方许可证正文，也不把“建议复用”写成“已接入”。

### 12.2 已登记的代码、库和平台依赖

| 类别 | 当前仓库事实 | 许可证/边界 |
|---|---|---|
| TrackerControl Android | gitlink 路径 `third_party/tracker-control-android/`，固定 commit `9504d41b9f6fa1509d784e5503c084d4b428307d`，登记 tag `2026080501`；官方来源为 [TrackerControl/tracker-control-android](https://github.com/TrackerControl/tracker-control-android) | 登记为 GPL-3.0；网络 VPN/TUN、DNS、连接记录和阻断能力属于第三方底座 |
| 团队补丁 | `third_party/patches/a4-3-serversinkhole-network-hook.patch` 与 `a5-1-domain-block-receiver.patch`；应用入口为 `scripts/apply-trackercontrol-hook.sh` | 属于对 GPL 底座的团队修改/适配，不改变底座原创归属；最终补丁适用性由 A8-4 核验 |
| AndroidX/Compose/Room/Kotlinx/Retrofit/OkHttp/测试依赖 | 已由构建文件和 [A7 许可证报告](a7-license-report.md)登记；主要为 Maven 依赖，未复制其源码 | 主要为 Apache-2.0；与 GPL 底座、数据许可证分开登记 |
| NetGuard、官方 samples、其他候选 | `docs/20` 和 `THIRD_PARTY_NOTICES.md` 作为调研/候选登记；不把候选项目写成当前实际接入 | 具体项目是否使用以实际登记为准 |

### 12.3 TrackerControl submodule 的核验状态

当前 worktree 的 `third_party/tracker-control-android` 是父仓库 gitlink，`.gitmodules` 指向官方仓库且固定到上述 commit；submodule 未初始化，因此本轮没有读取其源码或 `LICENSE`，也没有应用补丁、构建底座或修改 `third_party/`。仓库登记和 A7 文档已经记录补丁路径、用途、哈希和发布责任，但以下内容不能在本稿中提前宣称完成：

- 固定 commit 内实际 `LICENSE` 和单文件头部声明的最终核对；
- 固定 commit 内实际源码范围与最终 APK 对应关系；
- 两个补丁在固定 commit 上的完整应用检查；
- GPL 对应源码随最终提交材料提供的方式；
- 最终发布包中第三方源码、补丁、许可证和修改说明的完整对应关系。

以上事项属于 A8-4 的最终核验范围。

### 12.4 Disconnect 数据边界

已登记的 Disconnect Tracking Protection 数据来自 `services.json`，在当前仓库中以 TrackerControl bundled asset 为快照来源，父 commit 为 `9504d41b...`，bundled asset blob SHA 为 `6fa1d74b3dd74a174fe1a90af5d2b59edd7865dc`，数据许可证登记为 CC BY-NC-SA 4.0。脚本 [generate-tracker-dataset.py](../scripts/generate-tracker-dataset.py) 生成 100 条精简离线数据资产 [tracker-domains-v0.1.json](../app/src/main/assets/tracker-domains-v0.1.json)。

第三方数据只支持“该域名被公开列表分类为某类服务”的分类线索，不支持“发生了数据泄露”。团队原创边界是确定性提取/裁剪脚本、域名归一化与 `TrackerClassifier`、事件关联、规则、解释、UI 和评测；上游域名/实体/类别数据本身不属于团队原创。代码许可证、底座 GPL 义务和数据 CC BY-NC-SA 义务必须分别核对。

### 12.5 参考、适配、实际接入、原创实现

| 用语 | 本项目中的含义 |
|---|---|
| 参考 | 阅读官方文档、样例或候选项目以理解 API/架构；没有因此表示代码已导入 |
| 适配 | 在边界处把第三方/平台输出转换为 CausalGuard 契约，或通过登记补丁提供桥接；不等于拥有底座原始能力 |
| 实际接入 | 当前仓库的构建配置、gitlink、脚本、资产或代码已形成可追溯输入，并在对应事实来源中登记 |
| 原创实现 | 团队自行设计并实现的契约、规则、证据/因果解释、处置复查、UI、Demo、fixture 和评测逻辑 |

不能对外宣称为团队原创的内容包括 TrackerControl/NetGuard 的 VPN/TUN、TCP/UDP、DNS、连接记录、阻断核心及 Disconnect 的原始分类数据；不能把 Apache-2.0 库的通用实现或平台 API 本身写成团队发明。团队可以说明自己完成了适配、集成、上层契约和产品逻辑，但不能抹去第三方来源。

## 13. 当前状态、任务输入输出与依赖

### 13.1 当前完成状态

| 状态 | 当前可由仓库支持的事项 |
|---|---|
| 已完成/已合入事实 | 阶段 0～4 的设计、契约、Room/事件基础、规则和真实网络接入主链；B5-1～B5-7 场景/证据/因果/处置复查；B6-1～B6-6 本地解释、可选 AI 编排、评测与失败 registry；A7 RC 文档、构建记录、安装恢复说明和许可证报告；B7-1 Demo reset；B8-3 第三方与原创边界说明 |
| 进行中 | 阶段 8 的最终材料准备；B8-4 评测结果与失败案例的最终材料汇编；本源稿 B8-1 尚需形成正式 PDF |
| 待完成/待最终核验 | B7-2～B7-5 演示材料与独立运行验收；B8-2 截图和最终 MP4；B8-5 文案/媒体不夸大核对；A8-1～A8-5 最终设备回归、release 包、清理、GPL/submodule 核验与最终 tag |

A7 文档记录过 `0.2.0-rc1` 的 RC 构建事实，但这不等于 A8 最终 release APK、最终源码包、最终演示视频或最终设备回归已经完成。

### 13.2 B8-1 的输入、输出、依赖与验收

| 项 | 定义 |
|---|---|
| 输入 | README、docs/01～docs/21、A7 文档、第三方登记、当前 `app`/`core-model`/`rule-engine`/`demo-app` 结构、测试/fixture/构建配置 |
| 输出 | 唯一新增文件 `docs/b8-1-formal-design-document.md`，作为最终 PDF 的源稿 |
| 依赖 | 已合入 main 的设计/契约/实现事实；B8-3 仅作为边界说明来源；A8-1～A8-5、B8-2/B8-4/B8-5 的未完成结果不能被假定 |
| 本轮不做 | 不生成 PDF，不处理截图/视频/最终评测统计，不修改代码/已有文档/任务看板/第三方目录，不新增开源内容 |
| 验收 | 标题和章节完整；需求、架构、契约、数据流、规则、隐私、开源边界和状态可追溯；仓库内链接存在；`git diff --check` 通过；提交只含该源稿 |

## 14. 最终演示与验收映射

### 14.1 演示主线

最终演示计划以“异常发生 → 场景判断 → 因果解释 → 执行处置 → 复查变化”为主线：先完成授权和监测准备，再展示 Demo/真实网络事件，展开证据等级和解释边界，执行可逆建议，最后展示观察窗口中的复查结果或诚实的 `unknown`。

DEMO-B 的平台限制、DEMO-C 的 VPN 连接尝试、DEMO-D 的撤权后 probe 失败等结果必须按各自的事实类型陈述；synthetic high-risk fixture 只能作为可重复规则故事，不得写成 Android 16 真实设备已经发生的敏感数据外传。

### 14.2 验收映射

| 验收主题 | 设计文档对应章节 | 证据来源/最终核对 |
|---|---|---|
| 需求、用户场景、MVP | [§2](#2-项目背景问题定义与目标)、[§3](#3-mvp-范围非目标与-android-能力边界) | `docs/01`、`docs/04`；PDF 前核对与 APK 一致 |
| 架构和模块 | [§4](#4-总体架构与模块边界)、[§5](#5-端到端数据流) | `docs/05`、`docs/06`、当前工程目录 |
| 事件/规则/解释契约 | [§6](#6-核心数据契约与字段责任)、[§7](#7-规则引擎解释与安全边界) | `docs/07`、`docs/09`～`docs/11`、`core-model`/`rule-engine` |
| 真实/演示边界 | [§3.3](#33-android-能力边界)、[§8](#8-真实数据demosandbox数据与-fixture) | `docs/02`、`docs/16`、Demo 状态与标签 |
| 处置与复查 | [§9](#9-处置建议与复查闭环) | `docs/06`、`docs/07`、`docs/16`、处置/复查测试 |
| 测试与评测 | [§10](#10-测试评测与失败案例) | `docs/13`、`docs/14`、B6-5/B6-6、fixtures |
| 隐私与安全 | [§11](#11-隐私安全权限与日志) | `docs/03`、`docs/12`、A8-3 清理 |
| 开源与原创 | [§12](#12-开源复用与团队原创边界) | `THIRD_PARTY_NOTICES.md`、B8-3、A8-4 |
| 发布与最终验收 | [§13](#13-当前状态任务输入输出与依赖)、[§15](#15-生成最终-pdf-前需补齐的材料) | `docs/15`、`docs/16`、A7/A8 材料 |

## 15. 生成最终 PDF 前需补齐的材料

正式 PDF 生成和最终提交前，至少需要：

1. 完成并记录 A8-1 最终设备回归，包括冻结设备上的授权、VPN 生命周期、网络切换、Demo 场景、处置回执和复查结果；
2. 完成 A8-2 release APK、源码包、构建说明、依赖版本和版本一致性核对；
3. 完成 A8-3 密钥、账号、真实数据、原始日志和临时文件清理，并保留可审计的检查结果；
4. 完成 A8-4 对固定 TrackerControl commit 的 submodule 内容、实际 `LICENSE`、源码范围、两处补丁应用、GPL 对应源码提供方式和最终发布材料对应关系的核验；
5. 完成 A8-5 最终 tag，并让 PDF、源码、APK、截图、视频和文案指向同一发布事实；
6. 完成 B8-2 三张核心截图和最终 MP4，并确认不含真实个人信息、密钥或未验证功能；
7. 完成 B8-4 评测结果/失败案例附录的最终汇编，保持 rules-v0.1 与 rules-v0.2 分版本统计，并标注 curated/synthetic/N/A 限制；
8. 完成 B8-5 全部文案、截图和视频的能力边界核对，尤其是 unknown、Demo、连接尝试、权限状态和“数据泄露”措辞；
9. 补齐正式提交要求中的平台、版本、工具、实际 AI 模型名称/版本、接口方式和 AI 辅助代码比例；当前仓库已有 AI 契约和可选 Provider，但最终材料仍需填入真实发布信息；
10. 将本源稿排版为 PDF 后检查中文字体、目录、代码块、表格、链接、分页、图片清晰度和无敏感信息。

## 16. 事实来源

本源稿主要依据以下当前仓库文件；链接用于复查，不表示本稿替代这些契约或发布记录：

- [README.md](../README.md)
- [项目章程](01-project-charter.md)、[Android 能力边界](02-android-capability-matrix.md)、[权限与授权流程](03-permission-and-consent-flow.md)、[产品需求](04-product-requirements.md)
- [系统架构](05-system-architecture.md)、[模块设计](06-module-design.md)、[数据模型](07-data-model.md)、[事件契约](09-event-contract.md)、[风险规则契约](10-risk-rule-contract.md)、[AI 解释契约](11-ai-explanation-contract.md)、[隐私安全设计](12-privacy-security-design.md)
- [测试计划](13-test-plan.md)、[评测数据集](14-evaluation-dataset.md)、[验收清单](15-acceptance-checklist.md)、[演示与发布方案](16-demo-and-release-plan.md)
- [任务看板](17-task-board.md)、[风险清单](18-risk-register.md)、[工作导引](19-work-guide.md)、[开源复用指南](20-open-source-reuse-guide.md)、[并行工作分工](21-parallel-work-allocation-plan.md)
- [A7 安装·授权·清理·故障恢复](a7-install-auth-recovery.md)、[A7 发布候选记录](a7-release-candidate.md)、[A7 许可证报告](a7-license-report.md)、[第三方与原创边界说明](b8-3-third-party-original-boundary.md)
- [第三方登记](../THIRD_PARTY_NOTICES.md)、[TrackerControl 适配边界](trackercontrol-adapter-boundary.md)、[Demo 场景](demo-scenarios.md)、[网络核心映射](network-core-map.md)
- [Tracker 数据生成脚本](../scripts/generate-tracker-dataset.py)、[Tracker 精简资产](../app/src/main/assets/tracker-domains-v0.1.json)、[TrackerControl 补丁 1](../third_party/patches/a4-3-serversinkhole-network-hook.patch)、[TrackerControl 补丁 2](../third_party/patches/a5-1-domain-block-receiver.patch)

本源稿只新增自身文件；所有第三方代码、数据、模板和库均为仓库已有登记或依赖事实，本轮未新增任何开源内容。
