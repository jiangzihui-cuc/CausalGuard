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

## 5. 执行接口契约

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

## 6. 规则版本管理

- 规则文件带 `ruleVersion`，变更需登记到 `rule_version` 表；
- 评测时记录使用的规则版本，保证可复现；
- 规则变更与代码、文档同一提交。

## 7. 契约测试

- [ ] 同输入同版本输出稳定；
- [ ] 每条高风险结论都有 matchedRules 与 evidenceIds；
- [ ] 缺少证据时输出“无法确认”；
- [ ] 命中已知 tracker 时分类为 analytics；
- [ ] `unknown` 归属降低置信度而非强行归属。
