# 10 风险规则契约（事件 → 风险结果）

> 版本：`v0.1`（P0 设计基线）
> 最后更新：2026-09-20
> 责任人：成员 A / 成员 B
> 目的：冻结规则引擎的输入输出，使规则与页面解耦，便于评测。

## 1. 规则输入

```json
{
  "event": { "...见 09-event-contract.md..." },
  "appProfile": {
    "packageName": "com.example.calculator",
    "sceneType": "calculator",
    "declaredPermissions": ["INTERNET"],
    "grantedPermissions": ["INTERNET"]
  },
  "usageContext": {
    "foregroundState": "background",
    "lastUsedAgoMs": 7200000
  },
  "ruleVersion": "rules-v0.1"
}
```

## 2. 规则输出 RiskAssessment

```json
{
  "id": "r-20260920-0001",
  "eventId": "e-20260920-0001",
  "ruleVersion": "rules-v0.1",
  "riskScore": 78,
  "riskLevel": "high",
  "scenarioMatch": "mismatch",
  "confidence": "medium",
  "explanationBoundary": "缺少请求内容证据，不能判定是否发生数据泄露",
  "evidenceIds": ["ev-1", "ev-2"],
  "matchedRules": ["R-003", "R-006"],
  "category": "high_risk"
}
```

字段枚举：

| 字段 | 取值 |
|---|---|
| `riskLevel` | `low`(0-29) / `medium`(30-59) / `high`(60-79) / `critical`(80-100) |
| `scenarioMatch` | `match` / `match_with_concern` / `mismatch` / `unknown` |
| `confidence` | `low` / `medium` / `high` |
| `category` | `necessary` / `analytics` / `high_risk` / `unknown` |

## 3. 风险评分公式（初版）

```text
风险分 = 敏感性分 × 0.30
       + 后台行为分 × 0.25
       + 频率异常分 × 0.20
       + 场景不匹配分 × 0.15
       + 网络分类分 × 0.10
```

权重在测试阶段用样例校准，界面说明这只是辅助判断。

## 4. P0 规则清单（5-10 条本地规则）

| 规则 ID | 名称 | 条件 | 输出 |
|---|---|---|---|
| R-001 | 前台合理访问 | 场景匹配且前台使用中 | 合理，低风险 |
| R-002 | 后台访问敏感数据 | 后台 + clipboard/location/contacts | 需关注，中风险 |
| R-003 | 场景不匹配后台联网 | 场景不该联网的 App（如计算器）后台持续联网 | 高风险 |
| R-004 | 高频重复行为 | 同 dedupKey 在窗口内次数超阈值 | 频率异常加分 |
| R-005 | 已知分析追踪器 | 域名命中 tracker 映射表 | 分类为 analytics |
| R-006 | 长期未使用仍联网 | `lastUsedAgoMs` 超阈值 + 后台网络 | 高风险 |
| R-007 | 敏感数据伴随网络 | 敏感行为时间窗内伴随网络事件 | 提高风险等级 |
| R-008 | 无法归属网络 | network packageName=unknown | 置信度降为 low |
| R-009 | 权限已撤销仍观测 | permission revoked 后仍出现敏感访问 | 需要关注 |
| R-010 | 证据不足 | 关键字段缺失 | 输出 E5、无法确认 |

## 5. 规则资产 JSON 格式与加载边界

规则引擎加载的规则资产采用 JSON 格式。`docs/fixtures/risk-rules-v0.1.json` 是该格式的 v0.1 实例；后续真实实现可从可配置路径读取同结构资产，但运行时只允许读取已经通过 JSON 解析、版本、枚举、唯一性和引用校验的资产。

规则资产只定义“什么条件产生什么结构化风险结果”，不存放原始事件、不写入 `PrivacyEvent`，也不包含评测期望。事件数据、规则定义、测试预期必须分离：

- 事件数据：遵循 [09 事件契约](09-event-contract.md)，例如 `privacy-events-v0.1.json`；
- 规则定义：遵循本节规则资产格式，例如 `risk-rules-v0.1.json`；
- 测试预期：遵循 [14 评测数据集方案](14-evaluation-dataset.md) 的旁路格式，例如 `privacy-events-v0.1.expected.json`。

### 5.1 顶层结构

```json
{
  "schema": {
    "name": "risk-rules-v0.1",
    "purpose": "Human-readable purpose",
    "ruleVersion": "rules-v0.1",
    "contract": "../10-risk-rule-contract.md",
    "eventFixture": "privacy-events-v0.1.json",
    "expectationFixture": "privacy-events-v0.1.expected.json"
  },
  "rules": []
}
```

| 字段 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `schema.name` | 是 | string | 规则资产名称，应包含规则资产版本语义，如 `risk-rules-v0.1` |
| `schema.purpose` | 是 | string | 人类可读用途说明；可说明用于 fixture、演示或正式规则包，但不参与规则判定 |
| `schema.ruleVersion` | 是 | string | 规则版本，必须与每条规则的 `ruleVersion` 一致，并写入 `RiskAssessment.ruleVersion` |
| `schema.contract` | 是 | string | 指向本契约文档的相对路径，用于审计 |
| `schema.eventFixture` | 否 | string | 关联的事件样例文件；仅用于测试或评测，不参与运行时判定 |
| `schema.expectationFixture` | 否 | string | 关联的预期结果旁路文件；仅用于测试或评测，不参与运行时判定 |
| `rules` | 是 | array | 规则对象数组 |

### 5.2 规则对象

```json
{
  "id": "R-002",
  "ruleVersion": "rules-v0.1",
  "name": "后台访问敏感数据",
  "priority": 80,
  "condition": {},
  "output": {},
  "explanationBoundary": "只能说明发生了后台敏感访问，不能据此确认数据泄露。",
  "recommendation": {
    "action": "review_permission",
    "title": "检查权限和后台活动"
  },
  "degradation": {
    "shouldShowUnknownDegradation": false,
    "unknownHandling": "not_applicable"
  },
  "fixtureEventIds": ["e-20260921-0003"]
}
```

| 字段 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `id` | 是 | string | 规则 ID，格式使用 `R-001` 这类稳定编号；同一资产内必须唯一 |
| `ruleVersion` | 是 | string | 规则版本，必须等于 `schema.ruleVersion` |
| `name` | 是 | string | 规则名称，用于日志、评测和解释页面 |
| `priority` | 是 | number | 规则优先级，数值越大代表越先用于冲突处理或主解释选择；不得替代风险分 |
| `condition` | 是 | object | 触发条件，只能引用 `PrivacyEvent`、附加结构、`RuleInput.appProfile` 或 `RuleInput.usageContext` 中已有字段 |
| `output` | 是 | object | 命中后的结构化风险输出，字段取值必须遵循本契约枚举 |
| `explanationBoundary` | 是 | string | 解释边界，必须说明证据能支持什么、不能支持什么 |
| `recommendation` | 是 | object | 建议动作元数据，供 UI 或解释模板引用；不得表示已自动执行 |
| `degradation` | 是 | object | 降级策略，特别是 `unknown`、缺证据和无法归属时的处理 |
| `fixtureEventIds` | 否 | array<string> | 与测试 fixture 的对应事件 ID，仅用于测试覆盖和审计；运行时可忽略 |

### 5.3 condition 字段

`condition` 是声明式匹配条件。v0.1 支持以下键；后续新增键必须先更新本契约。

| 字段 | 类型 | 引用来源 | 说明 |
|---|---|---|---|
| `eventTypes` | array<string> | `PrivacyEvent.eventType` | 可取 `network`、`usage_context`、`clipboard`、`location`、`contacts`、`permission` |
| `foregroundStates` | array<string> | `PrivacyEvent.foregroundState` | 可取 `foreground`、`background`、`recent`、`unused`、`unknown` |
| `evidenceLevels` | array<string> | `PrivacyEvent.evidenceLevel` | 可取 `E1` 到 `E5` |
| `domainHints` | array<string> | `network.domainHint` | 仅用于域名线索匹配；为空时不得从 IP 强推 tracker 结论 |
| `packageName` | string | `network.packageName` 或 `appProfile.packageName` | 可用于 `unknown` 归属判断 |
| `uid` | number | `network.uid` | `-1` 表示无法归属 |
| `sceneTypes` | array<string> | `RuleInput.appProfile.sceneType` | 场景类型由 App 画像提供，不得从包名临时猜测后直接写入事件 |
| `scenarioMatchRequired` | string | 规则上下文 | 取值遵循 `scenarioMatch` 枚举，用于要求上游场景判断结果 |
| `timeWindowMs` | number | 事件时间戳 | 用于相关事件时间窗，不能删除或合并原始事件 |
| `relatedEventTypes` | array<string> | 相关事件集合 | 用于表达“敏感行为伴随网络”等跨事件规则 |
| `requiresPriorEvents` | array<object> | 事件库历史窗口 | 表达需要先前事件作为上下文，例如权限撤销后再出现位置访问 |
| `requiresPriorEvents[].eventType` | string | `PrivacyEvent.eventType` | 先前事件类型 |
| `requiresPriorEvents[].evidenceSummaryContains` | string | `PrivacyEvent.evidenceSummary` | 仅用于测试/演示的摘要关键词匹配；不得匹配敏感原文 |
| `notes` | string | - | 人类可读说明，不参与判定 |

触发条件不得引用 09 契约之外的事件字段，也不得要求采集层写入评测字段，如 `expectedRiskLevel` 或 `expectedMatchedRules`。

### 5.4 output、recommendation 与 degradation

`output` 对应 `RiskAssessment` 的核心字段：

| 字段 | 必填 | 允许值 |
|---|---|---|
| `output.riskLevel` | 是 | `low` / `medium` / `high` / `critical` |
| `output.category` | 是 | `necessary` / `analytics` / `high_risk` / `unknown` |
| `output.scenarioMatch` | 是 | `match` / `match_with_concern` / `mismatch` / `unknown` |
| `output.confidence` | 是 | `low` / `medium` / `high` |

`explanationBoundary` 必须使用中性措辞，不得出现“窃取”“恶意上传”“已泄露”等超出证据的结论。AI 解释层只能改写这些结构化结果，不能新增事实或升级风险。

`recommendation` 字段：

| 字段 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `recommendation.action` | 是 | string | 建议动作标识，如 `none`、`review_permission`、`limit_background_network`、`limit_background_activity` |
| `recommendation.title` | 是 | string | 面向用户的短标题 |

建议动作只表示“推荐展示什么”，不代表系统已经执行处置。`unknown` 或证据不足时，`recommendation.action` 应为 `none`，除非已有足够证据支持非确定性提示。

`degradation` 字段：

| 字段 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `degradation.shouldShowUnknownDegradation` | 是 | boolean | 是否在 UI/解释中展示 unknown 降级 |
| `degradation.unknownHandling` | 是 | string | 降级处理说明，必须说明不能确认的边界 |

### 5.5 unknown 安全降级规则

当事件出现以下情况之一时，应优先输出 `category=unknown`、`scenarioMatch=unknown`、`confidence=low`，并避免确定性处置建议：

- `network.packageName=unknown` 或 `network.uid=-1`；
- `domainHint` 为空且没有其他可靠分类来源；
- `evidenceLevel=E5`；
- 缺少场景、使用上下文或先前事件，导致规则前提不成立。

`unknown` 事件可以被保留、展示和计入失败/边界案例，但不得强行归属到某个 App，不得声称发生数据泄露，也不得生成“限制某 App”这类确定性处置建议。

### 5.6 版本与兼容

- 同一资产内 `id` 必须唯一；
- 同一资产内所有规则的 `ruleVersion` 必须等于 `schema.ruleVersion`；
- 规则引擎只能加载自己声明支持的 `ruleVersion`；
- 不兼容变更必须提升规则版本，并保留旧版本评测结果；
- 规则资产路径应可配置，避免把测试 fixture 路径硬编码为运行时路径；
- 加载前必须校验 JSON 可解析、必填字段存在、枚举合法、规则 ID 唯一、版本一致；
- 评测时还必须校验 `fixtureEventIds` 与 expected 文件中的 `expectedMatchedRules` 引用完整。

## 6. 执行接口契约

```kotlin
interface RiskRuleEngine {
    fun assess(input: RuleInput): RiskAssessment
}
```

约束：

- 规则只读事件，不修改原始事件；
- 同 `ruleVersion` 且同输入必须输出稳定结果；
- 规则异常不得阻断事件入库与页面展示，失败时返回最低置信度结果；
- 任何规则命中都必须能展开到 `evidenceIds`。

## 7. 规则版本管理

- 规则文件带 `ruleVersion`，变更需登记到 `rule_version` 表；
- 评测时记录使用的规则版本，保证可复现；
- 规则变更与代码、文档同一提交。

## 8. 契约测试

- [ ] 同输入同版本输出稳定；
- [ ] 每条高风险结论都有 matchedRules 与 evidenceIds；
- [ ] 缺少证据时输出“无法确认”；
- [ ] 命中已知 tracker 时分类为 analytics；
- [ ] `unknown` 归属降低置信度而非强行归属。
- [ ] 规则资产 JSON 可解析，规则 ID 唯一，版本一致；
- [ ] 规则资产中的条件只引用正式事件契约、App 画像或使用上下文字段；
- [ ] expected 文件引用的规则 ID 均存在于规则资产。
