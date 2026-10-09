# B6-1 本地解释模板与失败兜底

> 状态：已完成
> 实现：`rule-engine/src/main/kotlin/com/causalguard/rules/explain/LocalExplanationProvider.kt`
> 契约：[11 AI 解释契约](11-ai-explanation-contract.md) §5

## 职责

`LocalExplanationProvider` 是纯确定性、离线、无 I/O 的本地解释渲染器，按契约 §5 的五段结构把结构化 [ExplanationContext](../rule-engine/src/main/kotlin/com/causalguard/rules/explain/ExplanationModels.kt) 渲染为 `ExplanationText`：

| 字段 | 内容 |
|---|---|
| `summary` | `{appName} 在{foreground}状态下发生了{eventType}行为（共 {occurrenceCount} 次）。` |
| `whyCare` | 依据场景一致性结论与 `scenarioMatchReason`/`sceneType` 说明“为什么关注” |
| `evidence` | `依据：{evidenceSummary}`，缺失时退化为证据等级 |
| `action` | `建议：{recommendationTitle}`，缺失时“无需处置” |
| `caveat` | `说明：{explanationBoundary}` |

## 失败兜底语义

- `scenarioMatch = UNKNOWN`（证据不足/场景无法确认）时，`whyCare` 固定为 `当前证据不足以确认风险。`，绝不给出确定性结论；
- 所有字段在输入缺失时都退化为诚实的通用表述，不编造 App、时间、次数、事件类型或风险事实；
- 输出不包含“导致/证明生效/风险已消除/已经泄露/窃取”等因果升级或越界措辞；
- 同一输入恒产生同一输出（确定性）。

## 运行时接入

`FixtureEventAnalysisService` 的解释生成策略为：**事件专属模板优先，缺失时使用 `LocalExplanationProvider`**。因此 fixture 中已有的事件保持原有文案，真实/新增事件（无模板）走本地确定性模板兜底。`EventAnalysisResult.explanation` 类型统一为 `ExplanationText`。

断网、未配置密钥、模型超时/失败或 AI 输出未通过 B6-2 校验时，均回退本模板，保证 P0 主演示离线可用。
