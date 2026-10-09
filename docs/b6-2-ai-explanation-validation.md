# B6-2 AI 输入白名单、输出 schema 与本地事实校验

> 状态：已完成
> 实现：`rule-engine/src/main/kotlin/com/causalguard/rules/explain/AiExplanationRequest.kt`、`ExplanationFactValidator.kt`
> 契约：[11 AI 解释契约](11-ai-explanation-contract.md) §2、§3、§4

## 输入白名单

`AiExplanationRequest.from(context)` 是**唯一**允许发送给在线模型的结构，字段严格为契约 §2 的 12 个：

`task`、`locale`、`appName`、`eventType`、`foregroundState`、`riskLevel`、`scenarioMatch`、`category`、`matchedRules`、`evidenceLevel`、`occurrenceCount`、`explanationBoundary`。

本地专属字段（`packageName`、`sceneType`、`scenarioMatchReason`、`evidenceSummary`、`recommendationTitle`）**不进入**白名单，`from()` 负责丢弃。回归测试断言序列化结果恰好是这 12 个 key，且不含包名/域名/证据原文。

## 输出 schema

AI 输出 `ExplanationText`（`summary`/`whyCare`/`evidence`/`action`/`caveat`），与契约 §3 一致。

## 本地事实校验

`ExplanationFactValidator` 在本地执行，不依赖模型自检：

| 校验项 | 规则 | 不通过处理 |
|---|---|---|
| 字段完整 | 五段均非空 | `Rejected` |
| 数字一致 | 输出数字必须来自输入（`occurrenceCount` 或输入文本中的数字） | `Rejected` |
| App 一致 | 不得出现输入之外的包名样式 token（输入中已含的域名/包名允许） | `Rejected` |
| 类型一致 | 不得新增输入之外的事件类型词（输入文本已含的允许） | `Rejected` |
| 风险一致 | 不得出现高于输入 `riskLevel` 的升级词（高风险/严重/紧急/危急/极度危险/致命） | `Rejected` |
| 措辞 | 出现“窃取/恶意上传/已泄露/恶意/偷取”时替换为中性表述 | `Accepted`（`sanitized = true`） |

`Rejected` 表示丢弃模型输出并回退 B6-1 本地模板；`Accepted` 携带清洗后的 `ExplanationText`。校验是保守的启发式：宁可回退本地模板，也不放行可能越界或新增事实的输出。

## 边界

- 校验只读结构化输入与模型文本，不访问网络、不读 Repository；
- 不声称完成“全部事实一致性证明”，只覆盖可机械判定的数字/App/类型/风险/措辞；
- 完整事实一致率评测属 B6-5（见 [14 评测数据集方案](14-evaluation-dataset.md)）。
