# 07 数据模型

> 版本：`v0.1`（P0 设计基线）
> 最后更新：2026-09-24
> 责任人：成员 A（协作：成员 B）
> 目标：先冻结事件和证据的数据结构，避免后期页面和规则反复返工。

## 1. 实体总览

| 实体 | 说明 | 主要来源 |
|---|---|---|
| `AppProfile` | App 身份、权限、场景类型 | PackageManager |
| `UsageContextEvent` | 前后台与交互上下文 | UsageStatsManager |
| `NetworkEvent` | 网络连接元数据 | VpnService |
| `PrivacyEvent` | 统一事件（聚合各类型） | 采集适配器 + Demo |
| `EvidenceLink` | 事实之间的关联 | 证据关联引擎 |
| `RiskAssessment` | 风险评估结果 | 规则引擎 |
| `Recommendation` | 行动建议 | 处置模块 |
| `MitigationRecord` | 处置记录与复查结果 | 处置/复查模块 |
| `DemoScenario` | 沙箱场景真值 | Demo App |
| `RuleVersion` | 规则版本 | 配置模块 |
| `AuditLog` | AI 调用等审计记录 | 日志模块 |

## 2. 字段定义

### 2.1 AppProfile

| 字段 | 类型 | 说明 |
|---|---|---|
| `packageName` | String (PK) | 包名 |
| `appName` | String | 展示名 |
| `uid` | Int | Linux UID |
| `versionName` | String | 版本名 |
| `versionCode` | Long | 版本号 |
| `declaredPermissions` | String (JSON) | 声明权限列表 |
| `grantedPermissions` | String (JSON) | 已授权列表 |
| `sceneType` | String | calculator/weather/map/social/unknown |
| `isSystemApp` | Boolean | 是否系统应用 |
| `updatedAt` | Long | 更新时间戳 |

### 2.2 PrivacyEvent

| 字段 | 类型 | 说明 |
|---|---|---|
| `schemaVersion` | String | 契约版本，当前 `0.1`（见 [09](09-event-contract.md)） |
| `eventId` | String (PK) | UUID |
| `appId` | String | 包名，未知为 `unknown` |
| `appName` | String | 展示名 |
| `eventType` | String | clipboard/location/contacts/network/usage_context/permission |
| `timestamp` | Long | 事件发生时间 |
| `foregroundState` | String | foreground/background/recent/unused/unknown |
| `source` | String | system_api/usage_stats/vpn/demo/mock |
| `evidenceLevel` | String | E1/E2/E3/E4/E5 |
| `evidenceSummary` | String | 证据摘要（不含敏感原文） |
| `category` | String | necessary/analytics/high_risk/unknown |
| `riskScore` | Int | 0-100 |
| `confidence` | String | low/medium/high |
| `explanation` | String | 面向用户的解释 |
| `recommendationId` | String | 关联建议 |
| `isDemo` | Boolean | 是否演示数据 |
| `dedupKey` | String | 去重键 |
| `createdAt` | Long | 入库时间 |

### 2.3 UsageContextEvent

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long (PK auto) | 自增 |
| `packageName` | String | 包名 |
| `timestamp` | Long | 时间 |
| `state` | String | foreground/background |
| `screenOn` | Boolean | 屏幕是否点亮 |
| `source` | String | usage_stats/demo |

### 2.4 NetworkEvent

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long (PK auto) | 自增 |
| `eventId` | String | 关联 PrivacyEvent |
| `packageName` | String | 归属包名，未知 `unknown` |
| `uid` | Int | UID，未知 -1 |
| `protocol` | String | TCP/UDP |
| `remoteIp` | String | 目标 IP |
| `remotePort` | Int | 端口 |
| `domainHint` | String? | 域名线索，可能为空 |
| `bytesIn` | Long | 下行流量 |
| `bytesOut` | Long | 上行流量 |
| `timestamp` | Long | 时间 |
| `blocked` | Boolean | 是否被阻断 |
| `source` | String | vpn/demo |

### 2.5 EvidenceLink

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long (PK auto) | 自增 |
| `eventId` | String | 关联事件 |
| `linkedEventId` | String? | 关联的另一事件（如网络事件） |
| `relation` | String | temporal/app/rule |
| `evidenceLevel` | String | E1-E5 |
| `description` | String | 关联说明 |
| `ruleId` | String? | 命中的规则 |
| `createdAt` | Long | 时间 |

B5-2 中，`temporal` 和 `rule` 关联均属于 E3 Derived Inference：`temporal` 只表示事件的时间/先后关系，不表示因果、敏感内容传输或数据泄露；`rule` 表示规则基于事件事实得到的派生判断，不是 System Fact 或 Observed Fact。`app` 关系保留在契约中，但本版 builder 不强制生成。EvidenceLink 只在规则引擎中构建，不由 B5-2 自动持久化；no-match、version mismatch 和没有有效 supporting witness 时不制造 link。

### 2.6 RiskAssessment

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | String (PK) | v0.1 确定性当前评估 ID，格式为 `r-${eventId}`；不是 UUID |
| `eventId` | String | 关联事件 |
| `ruleVersion` | String | 规则版本 |
| `riskScore` | Int | 0-100 |
| `riskLevel` | String | low/medium/high/critical |
| `scenarioMatch` | String | match/match_with_concern/mismatch/unknown |
| `confidence` | String | low/medium/high |
| `category` | String | necessary/analytics/high_risk/unknown（见 10 契约 §2） |
| `explanationBoundary` | String | 解释边界说明 |
| `evidenceIds` | String (JSON) | `PrivacyEvent.eventId` 字符串引用列表；主事件始终第一个，effective rule 实际使用的 related/prior supporting event 随后加入；不等同于 `EvidenceLink.id` |
| `matchedRules` | String (JSON) | 命中的规则 ID 列表，供解释与评测展开（见 10 契约 §2） |
| `createdAt` | Long | 规则引擎输出为 `0` 时表示尚未分配持久化时间戳 |

v0.1 字段来源约束：

- `RiskAssessment.id` 是当前评估身份，固定为 `r-${eventId}`；一个 `PrivacyEvent` 在 v0.1 只维护一个当前 `RiskAssessment`，不定义同一事件的多 `ruleVersion` 历史版本存储。
- `RiskAssessment.createdAt = 0` 表示 `UNASSIGNED_AT_DETERMINISTIC_EVALUATION_BOUNDARY`，不是 Unix epoch 上真实发生的评估时间；真实持久化时间由后续集成契约定义。
- `RiskAssessment.evidenceIds` 使用 `PrivacyEvent.eventId` 作为逻辑证据引用。主事件始终包含且排在第一位；effective rule 实际使用的 related/prior supporting event 会被去重并按确定性顺序加入。它不等同于 `EvidenceLink.id`。
- 如果未来支持同一事件的多规则版本历史，必须同步升级 ID、DAO 与存储契约，不能只修改规则引擎。

### 2.7 Recommendation

| 字段 | 类型 | 说明 |
|---|---|---|
| `recommendationId` | String (PK) | UUID；B5-4 使用 deterministic name-based UUID |
| `riskType` | String | 对应风险类型 |
| `title` | String | 建议标题 |
| `reason` | String | 触发原因 |
| `systemPath` | String | 系统设置路径 |
| `expectedImpact` | String | 预计影响 |
| `reversible` | Boolean | 是否可逆 |
| `applicableVersion` | String | 适用系统版本 |
| `evidenceIds` | String (JSON) | 证据列表 |

B5-4 中，`Recommendation` 是建议/审计 DTO，不代表执行；同一评估事件、规则版本、来源规则、来源 action 与目标域名使用 UTF-8 name-based UUID 生成稳定 `recommendationId`。它与 `MitigationRequest`（可执行请求计划）分开，后者也不代表动作已经执行或生效。

### 2.8 MitigationRecord

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long (PK auto) | 自增 |
| `packageName` | String | 目标 App |
| `recommendationId` | String | 关联建议 |
| `action` | String | block_domain/block_app/open_settings |
| `target` | String | 阻断目标 |
| `executedAt` | Long | 执行时间 |
| `ruleVersion` | String | 规则版本 |
| `preSnapshot` | String (JSON) | 处置前快照 |
| `executionStatus` | String | 执行结果：executed/unavailable/unsupported/failed/unknown；失败/不可用不伪装成已执行 |
| `postResult` | String | reduced/no_change/unknown |
| `observationEnd` | Long? | 观察窗口结束时间 |
| `reviewNotes` | String? | 复查说明 |

### 2.9 DemoScenario

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | String (PK) | 场景编号 |
| `title` | String | 场景名 |
| `description` | String | 说明 |
| `groundTruth` | String (JSON) | 真值 |
| `expectedOutput` | String (JSON) | 预期规则输出 |
| `lastRunAt` | Long? | 最近运行时间 |

### 2.10 RuleVersion

| 字段 | 类型 | 说明 |
|---|---|---|
| `ruleVersion` | String (PK) | 版本号 |
| `description` | String | 说明 |
| `publishedAt` | Long | 发布时间 |
| `ruleCount` | Int | 规则数 |

### 2.11 AuditLog

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long (PK auto) | 自增 |
| `type` | String | ai_call/config_change/consent |
| `modelName` | String? | 模型名/版本 |
| `inputFields` | String? | 输入字段白名单（JSON） |
| `outputStatus` | String? | 成功/兜底/失败 |
| `createdAt` | Long | 时间 |

## 3. 关系

```text
AppProfile 1 ─── * PrivacyEvent
PrivacyEvent 1 ── * EvidenceLink
PrivacyEvent 1 ── 1 RiskAssessment
PrivacyEvent * ── 0..1 NetworkEvent (通过 eventId)
Recommendation 1 ── * MitigationRecord
DemoScenario 1 ── * PrivacyEvent (isDemo=true)
RiskAssessment * ── 1 RuleVersion
```

## 4. 数据最小化

- 不保存剪贴板/通讯录/精确位置原文；
- `evidenceSummary` 只存摘要与必要哈希/长度；
- 域名可脱敏；
- AI 请求前字段白名单过滤（见 12）。

## 5. 去重策略

- `dedupKey = hash(appId + eventType + foregroundState + 时间窗口桶)`；
- 同一 App 同类型行为在短时间窗口内合并计数，避免重复告警。

## 6. 变更规则

数据模型变更必须同步更新 [08-database-schema.sql](08-database-schema.sql)、[09-event-contract.md](09-event-contract.md) 和 [15-acceptance-checklist.md](15-acceptance-checklist.md)。
