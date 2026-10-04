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
| DEMO-C | 计算器后台联网与敏感行为时间相关 | `REAL` | VPN 元数据 + 时间窗关联 | `e-20260921-0004`，关联 `e-20260921-0003` | `high` / `analytics` | 展示网络元数据、tracker 分类和“伴随但不等于外传” |
| DEMO-D | 权限撤销后后台位置访问 | `HYBRID` | 系统权限事实 + Demo 真值 | `e-20260921-0008`，`e-20260921-0009` | `high` / `high_risk` | 展示先后因果链、权限复查和 unknown 降级口径 |

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

计算器后台联网与敏感行为时间相关

### 7.3 Demo purpose

展示 VPN 元数据、域名分类、场景不匹配和时间相关规则如何组合成高风险网络告警。

### 7.4 Mode

`REAL`。主事件 `e-20260921-0004` 来自 VPN 元数据；关联剪贴板事件是 Demo 真值，只作为时间窗上下文。

### 7.5 User steps

1. 在 DEMO-B 后保持 Demo Calculator 在后台。
2. 触发或回放后台连接分析域名的网络事件。
3. 打开 CausalGuard 事件详情页。
4. 展开规则命中、域名线索和证据边界。

### 7.6 Ground Truth

VPN 元数据观测到 Demo Calculator 在后台连接 `analytics.example.test:443`。该事实包含协议、端口、域名线索、UID 和包名；不包含请求体、请求参数或实际发送内容。

### 7.7 Corresponding fixture

| eventId | eventType | packageName / appId | source | evidenceLevel |
|---|---|---|---|---|
| `e-20260921-0004` | `network` | `com.demo.calculator` | `vpn` | `E2` |
| `e-20260921-0003` | `clipboard` | `com.demo.calculator` | `demo` | `E4` |

### 7.8 Expected context

| 字段 | 值 |
|---|---|
| `foregroundState` | `background` |
| `domainHint` | `analytics.example.test` |
| `scenarioMatch` | `mismatch` |
| `category` | `analytics` |
| `confidence` | `medium` |

### 7.9 Expected rules

| ruleId | why |
|---|---|
| `R-003` | 计算器场景后台联网不匹配 |
| `R-005` | 域名线索命中分析追踪类 fixture |
| `R-007` | 同应用时间窗内存在敏感事件与网络事件 |

### 7.10 Expected RiskAssessment

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
| 域名线索 | `Observed Fact` | `domainHint=analytics.example.test` |
| 后台剪贴板事件 | `Sandbox Ground Truth` | `e-20260921-0003` 在 3 秒前发生 |
| 场景与时间相关 | `Derived Inference` | 计算器后台联网且伴随敏感事件，命中 R-003/R-005/R-007 |
| 请求内容与数据流向 | `Unavailable / Unknown` | VPN 元数据不能读取请求体，不能确认外传 |

### 7.12 User explanation

Demo Calculator 在后台连接了分析域名。计算器场景通常不需要后台联网，且该连接与剪贴板事件处于相近时间窗；当前只能确认网络元数据和域名分类，不能确认请求内容或数据外传。

### 7.13 Recommended action

`limit_background_network`：建议限制该应用的后台网络访问，或在演示中触发阻断后复查连接变化。

### 7.14 Expected recheck

阶段 5 实现。

### 7.15 Failure/degradation path

如果 `domainHint` 缺失，不应用 `R-005`，只能显示 IP/端口和场景不匹配。若 UID 或包名无法归属，按 `e-20260921-0006` 的 unknown 路径处理，不把连接归到 Demo Calculator。

### 7.16 Competition demo wording

“这里展示的是高风险网络侧证据：后台联网、分析域名、计算器场景不匹配，并且和敏感访问时间相近。但系统只说伴随和风险，不做外传强断言。”

## 8. DEMO-D

### 8.1 Scenario ID

`DEMO-D`

### 8.2 Name

权限撤销后后台位置访问

### 8.3 Demo purpose

展示系统事实与 Demo 真值的先后因果链：权限状态变化本身不是风险，但可作为后续后台位置事件的重要上下文。

### 8.4 Mode

`HYBRID`。权限撤销来自 `system_api` 系统事实；后台位置访问来自 Demo App 沙箱真值。

### 8.5 User steps

1. 在系统设置中撤销 Demo Weather 的位置权限，或回放等价 permission fixture。
2. 触发 Demo Weather 的后台位置访问演示事件。
3. 打开 CausalGuard 查看权限事实、后台位置事件和证据链。
4. 展示 unknown 降级口径作为答辩边界。

### 8.6 Ground Truth

`e-20260921-0008` 记录权限状态从 `granted` 变为 `revoked`，这是系统 API 事实；`e-20260921-0009` 是 Demo Weather 在后台触发的位置访问演示事件。该组合用于演示异常场景，不声称真实系统权限被绕过。

### 8.7 Corresponding fixture

| eventId | eventType | packageName / appId | source | evidenceLevel |
|---|---|---|---|---|
| `e-20260921-0008` | `permission` | `com.demo.weather` | `system_api` | `E1` |
| `e-20260921-0009` | `location` | `com.demo.weather` | `demo` | `E4` |
| `e-20260921-0006` | `network` | `unknown` | `vpn` | `E5` |

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
| 权限撤销 | `System Fact` | `system_api` 记录 permission 从 `granted` 变为 `revoked` |
| 后台位置访问 | `Sandbox Ground Truth` | Demo Weather 触发 `location`，不保存坐标 |
| 先后关系 | `Derived Inference` | 后台位置事件发生在权限撤销事实之后 |
| 权限绕过与数据外传 | `Unavailable / Unknown` | 不能声称真实系统权限被绕过，不能确认位置数据被发送 |
| 无法归属网络 | `Unavailable / Unknown` | `e-20260921-0006` 保持 unknown，不推荐确定性处置 |

### 8.12 User explanation

Demo Weather 的位置权限先被撤销，随后出现后台位置访问演示事件。这个组合需要重点复查权限和演示状态，但不能据此声称真实系统权限被绕过，也不能确认位置数据被发送。

### 8.13 Recommended action

`review_permission`：建议复查位置权限和应用设置；unknown 网络事件只展示，不对具体 App 执行处置。

### 8.14 Expected recheck

阶段 5 实现。

### 8.15 Failure/degradation path

如果缺少 `e-20260921-0008` 权限撤销上下文，不应用 `R-009`，只展示后台位置访问与 `R-002`。如果出现 `packageName=unknown`、`uid=-1` 或 `evidenceLevel=E5`，必须走 unknown 降级，不归因到 Demo Weather。

### 8.16 Competition demo wording

“权限撤销事实本身不是风险；真正需要关注的是它之后出现的后台位置访问。我们把系统事实、Demo 真值和未知降级分开展示，避免夸大 Android 的实际观测能力。”

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
