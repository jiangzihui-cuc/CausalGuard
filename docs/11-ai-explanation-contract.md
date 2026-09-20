# 11 AI 解释契约（结构化结果 → 文案）

> 版本：`v0.1`（P0 设计基线）
> 最后更新：2026-09-20
> 责任人：成员 B（协作：成员 A）
> 目的：让 AI 只做解释层，输出受证据约束，失败时有本地模板兜底。

## 1. 原则

- AI 不直接决定权限，也不下结论“某 App 恶意”；
- 输入只能是脱敏、结构化的事件统计、场景判断和规则结果；
- 输出不得新增 App、时间、次数、事件类型或风险事实；
- 必须保留规则结果和原始证据；AI 失败时使用模板化文案兜底；
- 数字与事实以本地事件库为准，AI 只改写表达。

## 2. AI 输入（字段白名单）

只允许以下字段发送给模型：

```json
{
  "task": "explain_risk",
  "locale": "zh-CN",
  "appName": "计算器",
  "eventType": "clipboard",
  "foregroundState": "background",
  "riskLevel": "high",
  "scenarioMatch": "mismatch",
  "category": "high_risk",
  "matchedRules": ["R-002", "R-007"],
  "evidenceLevel": "E4",
  "occurrenceCount": 3,
  "explanationBoundary": "缺少请求内容证据，不能判定是否发生数据泄露"
}
```

**禁止发送**：包名以外的原始内容、剪贴板原文、通讯录、精确坐标、聊天内容、IP 全量列表、设备标识。

## 3. AI 输出

```json
{
  "summary": "计算器在你没有使用时读取了剪贴板，并伴随网络通信。",
  "whyCare": "计算器通常不需要剪贴板与联网，该行为与使用场景不匹配。",
  "evidence": "后台状态、读取剪贴板、检测到网络连接。",
  "action": "建议检查该 App 的后台活动，或限制其网络访问。",
  "caveat": "我们未看到通信内容，因此不能确认是否发生数据泄露。"
}
```

## 4. 输出约束校验

| 校验项 | 规则 | 不通过处理 |
|---|---|---|
| 数字一致 | 摘要中的次数/时间与输入一致 | 丢弃，走模板兜底 |
| App 一致 | 不得出现输入之外的 App | 丢弃 |
| 类型一致 | 不得新增事件类型 | 丢弃 |
| 风险一致 | 不得升级/降级输入的风险等级 | 丢弃 |
| 措辞 | 不得出现“窃取”“恶意上传”“已泄露” | 替换为中性表述 |

校验在本地执行，不依赖模型自检。

## 5. 本地模板兜底

模板按“发生了什么 / 为什么关注 / 证据是什么 / 可以怎么做 / 不确定性”五段生成：

```text
{appName} 在{foregroundState}状态下发生了{eventType}行为（共 {occurrenceCount} 次）。
由于{scenarioMatchReason}，该行为与{sceneType}场景{匹配结论}。
依据：{evidenceSummary}。
建议：{recommendationTitle}。
说明：{explanationBoundary}。
```

断网或模型失败时直接使用该模板，保证核心功能可用。

## 6. 审计记录

记录以下元数据到 `audit_log`，不保存敏感原文：

| 字段 | 说明 |
|---|---|
| `modelName` | 模型名称与版本 |
| `inputFields` | 实际发送字段白名单 |
| `outputStatus` | success / fallback / error |
| `createdAt` | 调用时间 |

最终提交文档需填写实际使用的大模型名称、版本、接口方式和 AI 辅助代码比例。

## 7. 契约测试

- [ ] AI 输出数字、App、时间、类型与输入一致；
- [ ] 断网时核心摘要仍可用（走模板）；
- [ ] 违规措辞被本地校验拦截；
- [ ] 事实一致率纳入评测（见 14）。
