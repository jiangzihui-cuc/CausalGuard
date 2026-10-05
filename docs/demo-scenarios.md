# Demo 场景与 Ground Truth 设计

> 版本：`v0.1`
> 最后更新：2026-09-24
> 责任人：成员 B
> 关联：`docs/09-event-contract.md`、`docs/10-risk-rule-contract.md`、`docs/11-ai-explanation-contract.md`、`docs/14-evaluation-dataset.md`、`docs/16-demo-and-release-plan.md`

## 1. 目标与边界

本文定义阶段 1 的 4 个 Ground Truth Demo 场景，用固定 fixture 支撑后续 Fake Repository、规则引擎、证据链、解释模板、UI 和演示脚本。

Demo App 只能证明“它自己在受控按钮或脚本触发下做了什么”。这类真值可以作为等价真实场景的评测样例，但不代表 Android 普通第三方应用能够普遍观测其他 App 的剪贴板、位置或通讯录调用。真实可观测能力仍以系统 API、UsageStats 和 VpnService 元数据为边界。

## 2. 模式定义

| 模式 | 含义 | 可作为 Ground Truth 的来源 | 展示要求 |
|---|---|---|---|
| `REAL` | 来自 Android 授权能力或系统事实 | `system_api`、`usage_stats`、`vpn` | 不展示 Demo 标识，但必须说明能力边界 |
| `SANDBOX` | 由 Demo App 自身触发并记录的受控事件 | `demo`，`isDemo=true` | 必须展示 Demo / 沙箱标识 |
| `HYBRID` | Demo 真值与真实元数据组合 | `demo` + `vpn` / `system_api` / `usage_stats` | 分开标注每条证据来源，不能混写 |

## 3. 证据等级与事实类型

| 类型 | 项目术语 | 示例 | 写作边界 |
|---|---|---|---|
| 系统事实 | `System Fact` | permission 状态从 `granted` 变为 `revoked` | 可作为上下文，不代表后续一定异常 |
| 授权观测事实 | `Observed Fact` | VPN 观测到 TCP 连接、UsageStats 观测到 recent 状态 | 只能说明元数据，不读取请求体 |
| 沙箱真值 | `Sandbox Ground Truth` | Demo App 记录自己读取剪贴板、访问位置或通讯录 | 只能证明 Demo App 自身行为 |
| 派生推断 | `Derived Inference` | 场景不匹配、时间窗内伴随网络 | 必须能追溯到规则和证据，不能新增事实 |
| 不可观测/未知 | `Unavailable / Unknown` | UID 无法归属、domainHint 为空、E5 | 保留 unknown，不强行归因或处置 |

## 4. 场景总览

| ID | 场景 | 模式 | 核心能力 | fixture | 预期风险 | 演示价值 |
|---|---|---|---|---|---|---|
| DEMO-A | 前台地图定位合理访问 | `SANDBOX` | Demo 真值 + 场景匹配 | `e-20260921-0001` | `low` / `necessary` | 建立低风险基线，说明不是所有敏感访问都异常 |
| DEMO-B | 后台剪贴板访问边界探测 | `SANDBOX` | 真实后台 probe + 平台限制结果 | 离线 synthetic fixture：`e-20260921-0003`、`e-20260921-0004` | 仅 fixture 评测 | 展示 Android 隐私保护与证据边界 |
| DEMO-C | 计算器后台最小网络探测 | `Runtime Ground Truth + Observed Fact` | Demo App 后台 probe；VPN 是否观察到另行记录 | 无 runtime synthetic fixture | 仅在真实观测支持时分析 | 展示后台连接事实与观测边界 |
| DEMO-D | 位置权限撤销后的访问边界探测 | `Runtime Ground Truth + Platform Boundary` | 权限状态检查 + 撤权后 location API probe | 无 runtime synthetic fixture | 不伪造权限绕过 | 展示平台拒绝、请求接受和未知边界 |

## 5. DEMO-A

### 5.1 Scenario ID

`DEMO-A`

### 5.2 Name

前台地图定位合理访问

### 5.3 Demo purpose

建立低风险基线：敏感类型事件不等于高风险，必须结合前后台状态、App 场景和规则结果判断。

### 5.4 Mode

`SANDBOX`。位置访问由 Demo App 受控触发并以 `isDemo=true` 标注，按等价真实前台地图定位评测。

### 5.5 User steps

1. 打开 Demo Map。
2. 进入前台导航页面。
3. 点击“开始导航”或等价触发按钮。
4. 返回 CausalGuard 查看事件时间线和详情页。

### 5.6 Ground Truth

Demo Map 在前台导航时触发 1 次位置访问。确定性来自 Demo App 自身日志和 fixture 中的 `source=demo`、`isDemo=true`；系统不保存坐标，只保存事件类型、前后台状态和摘要。

### 5.7 Corresponding fixture

| eventId | eventType | packageName / appId | source | evidenceLevel |
|---|---|---|---|---|
| `e-20260921-0001` | `location` | `com.demo.map` | `demo` | `E4` |

### 5.8 Expected context

| 字段 | 值 |
|---|---|
| `foregroundState` | `foreground` |
| `scenarioMatch` | `match` |
| `category` | `necessary` |
| `confidence` | `high` |

### 5.9 Expected rules

| ruleId | why |
|---|---|
| `R-001` | 地图导航场景下的前台位置访问与 App 场景匹配 |

### 5.10 Expected RiskAssessment

| 字段 | 预期值 |
|---|---|
| `riskLevel` | `low` |
| `confidence` | `high` |
| `matchedRules` | `["R-001"]` |
| `recommendation.action` | `none` |

### 5.11 Evidence chain

| 链路节点 | 类型 | 内容 |
|---|---|---|
| 前台导航触发位置访问 | `Sandbox Ground Truth` | Demo App 知道自己触发了 `location` |
| 前台状态 | `Observed Fact` | fixture 中 `foregroundState=foreground` |
| 地图导航需要定位 | `Derived Inference` | 场景知识库判断为 `match` |
| 通信内容与坐标轨迹 | `Unavailable / Unknown` | 不保存坐标，不读取网络请求内容 |

### 5.12 User explanation

Demo Map 在前台导航时访问了位置。地图导航场景需要当前位置，该行为与前台使用场景匹配；这是 Demo 真值事件，界面需要保留 Demo 标识。

### 5.13 Recommended action

`none`：无需处置，可继续观察后续是否出现后台访问或异常联网。

### 5.14 Expected recheck

阶段 5 实现。

### 5.15 Failure/degradation path

如果 Demo 触发失败，时间线显示空状态或错误状态，不生成风险结论。如果缺少场景画像，只能显示位置访问事实，`scenarioMatch` 降级为 `unknown`。

### 5.16 Competition demo wording

“这里先展示一个正常样例：即使是位置访问，只要发生在地图前台导航中，系统会给出低风险结论，避免把所有敏感权限都误报成危险行为。”

## 6. DEMO-B

### 6.1 Scenario ID

`DEMO-B`

### 6.2 Name

后台剪贴板访问边界探测

### 6.3 Demo purpose

展示普通后台 App 的剪贴板访问能力边界，并说明平台限制不是 Demo 功能失败。规则层的敏感访问和网络伴随风险另由独立 synthetic fixture 评测。

### 6.4 Mode

`SANDBOX`。Demo Calculator 在退到后台后由 `onStop` 真实调用 `ClipboardManager.primaryClip`。目标设备上的平台拒绝、空结果或不可用结果均显示为 `Platform Restricted`；只有平台确实返回内容时才产生脱敏 Demo 事件。

### 6.5 User steps

1. 复制一段无隐私测试文本。
2. 打开 Demo Calculator，点击 `Arm DEMO-B`。
3. 按 Home 将 App 切到后台；Demo App 在 `onStop` 中真实执行 clipboard probe。
4. 返回 Demo Calculator，展示 `Platform Restricted` 及其探测结果。

### 6.6 Ground Truth

目标设备 OPPO PJW110 / Android 16（API 36）的 Runtime Ground Truth 是：后台 probe 确实执行，系统拒绝普通后台应用访问，未获得剪贴板内容，也未生成 `clipboard` PrivacyEvent。应用不读取、保存或展示剪贴板原文。

跨平台的成功分支仍只保存返回内容的长度，并生成 `foregroundState=background`、`source=demo`、`evidenceLevel=E4` 的 canonical Demo 事件。

### 6.7 Corresponding fixture

| eventId | eventType | packageName / appId | source | evidenceLevel |
|---|---|---|---|---|
| `e-20260921-0003` | `clipboard` | `com.demo.calculator` | `demo` | `E4` |
| `e-20260921-0004` | `network` | `com.demo.calculator` | `vpn` | `E2` |

以上两条是离线 deterministic rule evaluation 的 synthetic fixture，不是 Android 16 真机 probe 产生的事件。它们用于独立验证 `R-002`、`R-007` 以及“时间相关不等于外传证明”。

### 6.8 Expected context

| 字段 | `e-20260921-0003` | `e-20260921-0004` |
|---|---|---|
| `foregroundState` | `background` | `background` |
| `scenarioMatch` | `mismatch` | `mismatch` |
| `category` | `high_risk` | `analytics` |
| `confidence` | `medium` | `medium` |

### 6.9 Expected rules

| ruleId | why |
|---|---|
| `R-002` | 后台发生 `clipboard` 敏感访问 |
| `R-007` | 同应用时间窗内存在敏感事件与网络事件 |

### 6.10 Expected RiskAssessment

仅在离线 synthetic fixture 评测中，以 `e-20260921-0003` 作为主事件：

| 字段 | 预期值 |
|---|---|
| `riskLevel` | `high` |
| `confidence` | `medium` |
| `matchedRules` | `["R-007", "R-002"]` |
| `recommendation.action` | `limit_background_network` |

### 6.11 Evidence chain

| 链路节点 | 类型 | 内容 |
|---|---|---|
| 后台 clipboard probe | `Runtime Ground Truth` | `onStop` 中真实执行；目标设备被平台拒绝 |
| 平台结果 | `Observed Fact` | `Platform Restricted`，未获得内容，不生成 PrivacyEvent |
| 离线剪贴板 fixture | `Synthetic Evaluation Input` | 仅用于规则层回归，不是真机事件 |
| 剪贴板内容是否外传 | `Unavailable / Unknown` | 不保存原文，也未读取网络请求体 |

### 6.12 User explanation

真机演示展示的是平台能力边界：Demo Calculator 退到后台后真实尝试访问剪贴板，Android 16 系统直接拒绝。我们不会为了演示效果伪造成功读取，也不会生成虚假的隐私事件。离线 synthetic fixture 中的同应用网络时间相关只用于规则评测，不能证明内容被发送。

### 6.13 Recommended action

真机 `Platform Restricted` 不生成处置建议或风险事件；离线 fixture 命中规则时，`limit_background_network` 仍只是规则层建议，不表示已经执行。

### 6.14 Expected recheck

阶段 5 实现。

### 6.15 Failure/degradation path

平台拒绝、`null` 或空结果是预期的 `Platform Restricted` 降级：显示 probe 已执行、访问被系统限制、未生成 PrivacyEvent、未读取或保存原文。其他非预期异常才显示 `Failure`。离线 fixture 与 Runtime probe 分开展示，不能用 fixture 代替真机事实。

### 6.16 Competition demo wording

“这个场景专门展示系统能力边界：Demo Calculator 退到后台后真实尝试访问剪贴板，Android 16 系统直接拒绝。我们不伪造成功读取，也不生成虚假的隐私事件；规则层的高风险案例使用独立 synthetic fixture 做可复现评测。”

## 7. DEMO-C

### 7.1 Scenario ID

`DEMO-C`

### 7.2 Name

计算器后台最小网络探测

### 7.3 Demo purpose

验证 Demo Calculator 退到后台后确实主动执行一次无敏感内容的最小 TCP probe。VPN 是否观察到该连接属于独立的 Observed Fact；本场景本身不制造 tracker 命中或风险结论。

### 7.4 Mode

Runtime Ground Truth 是 Demo Calculator 自己执行的后台 TCP probe；如果 CausalGuard 的 VPN 链路观察到连接，才产生独立的 `source=vpn` 观测事件。Demo App 不生成 `PrivacyEvent`。

### 7.5 User steps

1. 打开 Demo Calculator，点击 `Arm DEMO-C`。
2. 按 Home 使 Activity 进入 `onStop`。
3. Demo App 发起一次到 `example.com:443` 的最小 TCP connection。
4. 返回 Demo Calculator 查看 probe 结果；再到 CausalGuard 检查 VPN 是否观察到对应元数据。

### 7.6 Ground Truth

Demo Calculator 在后台执行了一次到 `example.com:443` 的 TCP probe。Demo App 只记录 probe 成功或失败；若 VPN 观察到连接，协议、端口、UID、包名和 domainHint 由 CausalGuard 链路独立提供。Runtime C 不声称 tracker 命中、analytics、R-005、R-007 或 clipboard correlation。

### 7.7 Synthetic evaluation input（不是 Runtime 输出）

| eventId | eventType | packageName / appId | source | evidenceLevel |
|---|---|---|---|---|
| `e-20260921-0004` | `network` | `com.demo.calculator` | `vpn` | `E2` |
| `e-20260921-0003` | `clipboard` | `com.demo.calculator` | `demo` | `E4` |

以上仅用于规则层 deterministic evaluation，不能被写成 Runtime DEMO-C 已产生的事件，也不能替代真实 VPN 观察结果。

### 7.8 Synthetic expected context（仅离线评测）

| 字段 | 值 |
|---|---|
| `foregroundState` | `background` |
| `domainHint` | `analytics.example.test` |
| `scenarioMatch` | `mismatch` |
| `category` | `analytics` |
| `confidence` | `medium` |

### 7.9 Synthetic expected rules

| ruleId | why |
|---|---|
| `R-003` | 计算器场景后台联网不匹配 |
| `R-005` | 域名线索命中分析追踪类 fixture |
| `R-007` | 同应用时间窗内存在敏感事件与网络事件 |

### 7.10 Synthetic expected RiskAssessment

以 `e-20260921-0004` 作为主事件：

| 字段 | 预期值 |
|---|---|
| `riskLevel` | `high` |
| `confidence` | `medium` |
| `matchedRules` | `["R-003", "R-005", "R-007"]` |
| `recommendation.action` | `limit_background_network` |

### 7.11 Evidence chain

| 链路节点 | 类型 | 内容 |
|---|---|---|
| 后台网络连接 | `Observed Fact` | VPN 记录 `protocol=TCP`、`remotePort=443`、`uid=10123` |
| Demo-C 后台 probe | `Runtime Ground Truth` | Demo App 自己执行 `example.com:443` TCP probe |
| synthetic 域名/剪贴板事件 | `Synthetic Evaluation Input` | `e-20260921-0003`/`0004` 仅用于离线规则回归 |
| 场景与时间相关 | `Derived Inference` | 仅在 synthetic evaluation 中计算 R-003/R-005/R-007 |
| 请求内容与数据流向 | `Unavailable / Unknown` | VPN 元数据不能读取请求体，不能确认外传 |

### 7.12 User explanation

Demo Calculator 在后台主动执行了一次最小 TCP probe。Demo App 只能证明自己发起了连接；VPN 是否观察到、是否有域名线索和 UID 归属必须以 CausalGuard 的真实观测为准，不能把该 probe 自动解释成 tracker 命中或隐私泄露。

### 7.13 Recommended action

Runtime DEMO-C 不生成风险处置建议。离线 synthetic fixture 的规则建议仍仅属于评测结果，不表示 Runtime probe 已被分类或已执行处置。

### 7.14 Expected recheck

阶段 5 实现。

### 7.15 Failure/degradation path

如果 Runtime probe 没有被 VPN 观察到，只记录 Demo App 自己的 probe 结果；不能补造 `source=vpn` 事件。若真实观测缺少 domainHint、UID 或包名，按 B4-3 的诚实降级展示。`e-20260921-0003`/`0004` 的 R-003/R-005/R-007 仅限 synthetic evaluation。

### 7.16 Competition demo wording

“这里先证明 Demo Calculator 在后台确实主动发起了一个最小 TCP probe；随后由 CausalGuard 的真实 VPN 链路报告是否观察到连接。没有 VPN 观测就不补造事件，也不把 probe 说成 tracker 命中或数据外传。”

## 8. DEMO-D

### 8.1 Scenario ID

`DEMO-D`

### 8.2 Name

位置权限撤销后的访问边界探测

### 8.3 Demo purpose

展示权限基线、用户撤权和受控 location API probe 的真实平台结果；权限被拒绝是有效的 Platform Restricted 结果，不伪造权限绕过。

### 8.4 Mode

Runtime DEMO-D 是明确状态机：`Ready → GrantedBaselineRecorded → RevokedConfirmed → Armed → PlatformRestricted / ProbeSucceeded / Failure`。本轮 Runtime 不生成 location `PrivacyEvent`；现有 `e-0008`/`e-0009` 只作为 synthetic evaluation input。

### 8.5 User steps

1. 打开 Demo Weather；必要时请求位置权限。
2. 点击“记录已授权基线”，真实检查权限已 granted。
3. 打开应用权限设置并撤销位置权限。
4. 返回后确认 revoked，点击 `Arm DEMO-D`，再按 Home。
5. Demo App 在 `onStop` 真实调用 location API，记录平台结果；不保存坐标、accuracy 或轨迹。

### 8.6 Ground Truth

Runtime 只记录真实权限检查、撤权确认和 location API probe 状态。若 `SecurityException`、当前权限 denied 或平台明确拒绝，结果为 `PlatformRestricted`；若 API 请求未立即被拒绝但没有可靠位置回调，只记录 `RequestAccepted` 语义，不能声称获得了位置数据。

### 8.7 Synthetic evaluation input（不是 Runtime 输出）

| eventId | eventType | packageName / appId | source | evidenceLevel |
|---|---|---|---|---|
| `e-20260921-0008` | `permission` | `com.demo.weather` | `system_api` | `E1` |
| `e-20260921-0009` | `location` | `com.demo.weather` | `demo` | `E4` |
| `e-20260921-0006` | `network` | `unknown` | `vpn` | `E5` |

这些事件继续用于规则评测；Runtime DEMO-D 不构造 `source=system_api` 的 permission event，也不构造成功 location event。

### 8.8 Expected context

| 字段 | `e-20260921-0008` | `e-20260921-0009` | unknown 降级示例 |
|---|---|---|---|
| `foregroundState` | `recent` | `background` | `unknown` |
| `scenarioMatch` | `match_with_concern` | `mismatch` | `unknown` |
| `category` | `necessary` | `high_risk` | `unknown` |
| `confidence` | `high` | `medium` | `low` |

### 8.9 Expected rules

| ruleId | why |
|---|---|
| `R-002` | 后台发生 `location` 敏感访问 |
| `R-009` | 同应用存在先前 permission revoked 上下文 |
| `R-008` | unknown 降级示例：网络事件无法归属 |
| `R-010` | unknown 降级示例：`evidenceLevel=E5`，证据不足 |

### 8.10 Expected RiskAssessment

以 `e-20260921-0009` 作为主事件：

| 字段 | 预期值 |
|---|---|
| `riskLevel` | `high` |
| `confidence` | `medium` |
| `matchedRules` | `["R-002", "R-009"]` |
| `recommendation.action` | `review_permission` |

unknown 降级样例 `e-20260921-0006` 的预期为 `riskLevel=low`、`confidence=low`、`matchedRules=["R-008", "R-010"]`、`recommendation.action=none`。

### 8.11 Evidence chain

| 链路节点 | 类型 | 内容 |
|---|---|---|
| 权限基线与撤销确认 | `Runtime Ground Truth` | Demo Weather 真实检查 granted → revoked |
| 撤权后 location probe | `Platform Boundary` | API 被拒绝、请求接受或发生回调，均不保存位置内容 |
| synthetic 权限/位置事件 | `Synthetic Evaluation Input` | `e-20260921-0008`/`0009` 仅用于规则回归 |
| 权限绕过与数据外传 | `Unavailable / Unknown` | 不能声称真实系统权限被绕过，不能确认位置数据被发送 |
| 无法归属网络 | `Unavailable / Unknown` | `e-20260921-0006` 保持 unknown，不推荐确定性处置 |

### 8.12 User explanation

Demo Weather 先记录授权基线，再由用户撤销位置权限，随后真实尝试调用 location API。Android 拒绝访问时显示 Platform Restricted；即使请求未立即抛错，也不能据此声称获得了位置数据。Runtime 不生成 location PrivacyEvent。

### 8.13 Recommended action

Runtime DEMO-D 不生成处置建议。Synthetic fixture 的 `review_permission` 只属于离线评测结果；unknown 网络事件只展示，不对具体 App 执行处置。

### 8.14 Expected recheck

阶段 5 实现。

### 8.15 Failure/degradation path

如果 Runtime 没有记录 granted baseline 或无法确认 revoked，不允许 Arm DEMO-D，也不执行后台 probe。若平台结果是 Failure，保留异常类型；若结果是 PlatformRestricted，不生成 PrivacyEvent。Synthetic fixture 缺少 `e-20260921-0008` 时不应用 `R-009`，其中 `packageName=unknown`、`uid=-1` 或 `evidenceLevel=E5` 继续走 unknown 降级。

### 8.16 Competition demo wording

“Demo Weather 先记录已授权基线，再由用户撤销权限并真实调用 location API。平台拒绝时展示 Platform Restricted；我们不伪造成功定位，也不把 synthetic evaluation 事件写成 Android 16 Runtime 事实。”

## 9. 评测与验收

| 检查项 | 验收标准 |
|---|---|
| fixture 可追溯 | 每个场景至少引用一个 `privacy-events-v0.1.json` 中存在的 `eventId` |
| 规则可追溯 | 每个预期规则均存在于 `risk-rules-v0.1.json` |
| expected 一致 | `riskLevel`、`category`、`scenarioMatch`、`confidence`、`matchedRules`、`recommendation.action` 与 `privacy-events-v0.1.expected.json` 保持一致 |
| 边界诚实 | 不出现超出证据的强断言或定性结论 |
| Demo 标识 | `source=demo` 或 `isDemo=true` 的事件必须说明沙箱性质 |
| unknown 降级 | UID、包名、域名或证据等级不足时不强行归因、不推荐确定性处置 |

## 10. 后续接入点

| 阶段 | 接入方式 |
|---|---|
| 阶段 3 | Fake Repository 按本文场景顺序回放 fixture |
| 阶段 5 | 证据链、Recommendation、RecheckComparator 使用本文作为验收脚本 |
| 阶段 6 | 本地解释模板和 AI 输出校验复用本文的事实/推断/未知边界 |
| 阶段 7 | 3 分钟演示脚本从 DEMO-A 到 DEMO-D 选择 2-3 个核心片段串联 |
