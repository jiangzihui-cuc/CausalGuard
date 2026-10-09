# B6-2 AI 输入白名单、输出 schema 与本地事实校验

> 状态：已完成
> 实现：`rule-engine/src/main/kotlin/com/causalguard/rules/explain/AiExplanationRequest.kt`、`ExplanationFactValidator.kt`
> 契约：[11 AI 解释契约](11-ai-explanation-contract.md) §2、§3、§4

## 输入白名单

`AiExplanationRequest.from(context)` 是**唯一**允许发送给在线模型的结构，也是事实校验的输入边界，字段严格为契约 §2 的 12 个：

`task`、`locale`、`appName`、`eventType`、`foregroundState`、`riskLevel`、`scenarioMatch`、`category`、`matchedRules`、`evidenceLevel`、`occurrenceCount`、`explanationBoundary`。

本地专属字段（`packageName`、`sceneType`、`scenarioMatchReason`、`evidenceSummary`、`recommendationTitle`）**不进入**白名单，`from()` 负责丢弃。回归测试断言序列化结果恰好是这 12 个 key，且不含包名/域名/证据原文。

## 输出 schema

AI 输出 `ExplanationText`（`summary`/`whyCare`/`evidence`/`action`/`caveat`），与契约 §3 一致。

## 本地事实校验

`ExplanationFactValidator` 接收实际发送的 `AiExplanationRequest` 与模型输出，在本地执行，不依赖模型自检。未进入 request 的本地字段不能作为 AI 输出的事实依据：

| 校验项 | 规则 | 不通过处理 |
|---|---|---|
| 字段完整 | 五段均非空 | `Rejected` |
| 数字一致 | 输出数字必须来自输入（`occurrenceCount` 或输入文本中的数字） | `Rejected` |
| App 一致 | 不得出现 request 之外的包名样式 token | `Rejected` |
| 类型一致 | 不得新增输入之外的事件类型词（输入文本已含的允许） | `Rejected` |
| 风险一致 | 明确出现的风险词必须与输入 `riskLevel` 一致；既拒绝升级也拒绝降级（低风险/中风险/中等风险/高风险/严重/紧急/危急/极度危险/致命） | `Rejected` |
| 措辞 | 出现“窃取/恶意上传/已泄露/恶意/偷取”时替换为中性表述 | `Accepted`（`sanitized = true`） |

`Rejected` 表示丢弃模型输出并回退 B6-1 本地模板；`Accepted` 携带清洗后的 `ExplanationText`。校验是保守的启发式：宁可回退本地模板，也不放行可能越界或新增事实的输出。输出未提及风险等级时不会仅因缺失风险词而拒绝。

## 边界

- 校验只读结构化输入与模型文本，不访问网络、不读 Repository；
- 不声称完成“全部事实一致性证明”，只覆盖可机械判定的数字/App/类型/风险/措辞；
- 完整事实一致率评测属 B6-5（见 [14 评测数据集方案](14-evaluation-dataset.md)）。
