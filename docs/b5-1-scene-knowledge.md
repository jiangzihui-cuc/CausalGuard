# B5-1 场景知识库与一致性判断

> 状态：已完成
> 资产：`docs/fixtures/scene-knowledge-v0.1.json`
> 运行时副本：`app/src/main/assets/scene-knowledge-v0.1.json`

## 目的

B5-1 建立一个小而确定、可版本化、可审计的场景知识资产，并提供纯 Kotlin 的 `SceneConsistencyEvaluator`，将 `PrivacyEvent + AppProfile.sceneType` 映射为 `ScenarioMatch`，供后续 `RuleInput`、`EvidenceLink` 与因果链使用。

场景知识是团队定义的 rule knowledge / derived inference，不是 Android System Fact、VPN Observed Fact，也不是 Demo Ground Truth。

## 输入字段

`SceneConsistencyEvaluator` 只读取：

- `PrivacyEvent.eventType`
- `PrivacyEvent.foregroundState`
- `AppProfile.sceneType`

它不读取也不推断：

- `packageName` / `appName`
- `domainHint` / tracker 分类 / `blocked`
- `riskScore` / `category` / `confidence` / `recommendationId`
- `RiskAssessment` / `matchedRules`
- `evidenceSummary` 文本关键词

场景判断是风险规则的上游输入，不能从风险结果反向构造。

## Deterministic Matching

匹配过程固定为：

1. `appProfile == null` -> `unknown`
2. `sceneType` blank 或 `unknown` -> `unknown`
3. `foregroundState == unknown` -> `unknown`
4. 精确匹配 `sceneType + eventType + foregroundState`
5. 找到唯一知识规则 -> 返回该规则的 `result`
6. 未找到规则 -> `unknown`

缺少知识不代表不匹配。知识库中没有明确规则时，必须返回 `unknown`，不能降级成 `mismatch`。

## v0.1 Knowledge

v0.1 只冻结当前已有主演示和 fixture 依据的四条知识：

| id | sceneType | eventType | foregroundState | result |
|---|---|---|---|---|
| SCENE-MAP-001 | map | location | foreground | match |
| SCENE-MAP-002 | map | network | foreground | match |
| SCENE-CALC-001 | calculator | clipboard | background | mismatch |
| SCENE-CALC-002 | calculator | network | background | mismatch |

`weather` 和 `social` 暂无足够冻结依据，v0.1 不定义行为；收到这些 sceneType 且没有明确知识规则时返回 `unknown`。

## Frozen Context Boundary

`docs/fixtures/rule-input-context-v0.1.json` 是既有 frozen synthetic evaluation context，继续手工提供 `scenarioMatches`，用于保持规则评测可复现。

B5-1 的 evaluator 不修改该 frozen context，也不接入当前 runtime analysis path。新增测试只把 `e-20260921-0001` 到 `e-20260921-0004` 作为回归对照，确认新 evaluator 的推导与 frozen context 一致。

后续 B5-2 可以在构建 `EvidenceLink` 时引用 `knowledgeRuleId`，但本轮不实现证据链或因果链。
