# B5-4 Recommendation 选择

> 状态：已完成
> 实现：`rule-engine/src/main/kotlin/com/causalguard/rules/RecommendationSelector.kt`

## 三层边界

`RecommendationDecision` 是规则资产给出的原始建议元数据（`action + title`）；
`Recommendation` 是确定性生成的 canonical 展示/审计 DTO；
`MitigationRequest` 是可交给执行层的请求计划；`MitigationExecution` 是 A 侧真实执行结果。本轮只构造前三者中的前三层，不构造或调用执行结果。它们不是同一个概念：

- `Recommendation` 不代表动作已经执行；
- `MitigationRequest` 也不代表动作已经执行或已经生效；
- 本轮不调用 `MitigationExecutor`，不持久化 Recommendation/Request，也不接 UI。

## 选择规则

- 正常 `none` 保留规则标题，例如 R-001 的“无需处置”；这与 unknown 的“不确定、暂不处置”不同。
- `assessment.category=unknown` 或 unknown degradation 时，Recommendation 仍生成，但标题收敛为“无法确认，暂不处置”，永不生成 executable request。
- 规则版本不匹配保留“规则版本不匹配，暂不处置”的安全表达，并且没有 request。
- `review_permission` 和 `limit_background_activity` 仅在包名可靠时映射为 `OPEN_SETTINGS`；打开设置页不表示权限或后台行为已经改变。
- `limit_background_network` 只在 `assessment.evidenceIds` 中存在可靠域名证据时映射为 `BLOCK_DOMAIN`，不映射 `BLOCK_APP`，也不在缺域名时 fallback 到 `OPEN_SETTINGS`。

域名候选必须同时满足：证据 ID 在 `assessment.evidenceIds` 中、与 primary 使用同一可靠 App、存在非空 `domainHint`、网络归属包名不是 `unknown` 且与 primary App 一致。选择顺序是 primary event、`sourceRuleId` 对应 temporal link 的 linked event、其余 evidenceIds 顺序。不会从 IP、规则标题、tracker 分类或 AI 推断域名。

`recommendationId` 使用 Java 标准库 `UUID.nameUUIDFromBytes` 和 UTF-8 seed，seed 包含 event、rule version、来源规则、来源 action 与目标域名，因此相同评估输入稳定复现。`Recommendation.evidenceIds` 原样复制 `RiskAssessment.evidenceIds`。

## P0 边界

当前 selector 只产生 `OPEN_SETTINGS` 和有可靠目标的 `BLOCK_DOMAIN`。它不执行真实处置，不写 Room/Repository，不修改风险、证据或因果链语义。`expectedImpact` 只描述“若用户执行”和“需以执行回执/复查为准”，不声称已阻断、已修改权限或已生效。
