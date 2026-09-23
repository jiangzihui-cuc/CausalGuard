# 14 评测数据集方案

> 版本：`v0.1`（P0 设计基线）
> 最后更新：2026-09-20
> 责任人：成员 B（协作：成员 A）
> 目的：建立可复现、带标签的评测集，用数据证明“场景推理”和“处置闭环”的价值。

## 1. 评测集构成（目标 30-50 条）

每条样例包含：

- App 场景；
- 事件类型（clipboard/location/contacts/network/usage_context）；
- 前后台状态；
- 预期分类（necessary/analytics/high_risk/unknown）；
- 预期风险等级（low/medium/high/critical）；
- 预期场景一致性（match/mismatch/...）；
- 推荐处置；
- 证据来源；
- 类型标签（正常/异常/场景不匹配/无法确认边界）。

样例示例如下（JSON）：

```json
{
  "id": "S-001",
  "appScene": "calculator",
  "eventType": "clipboard",
  "foregroundState": "background",
  "expectedCategory": "high_risk",
  "expectedRiskLevel": "high",
  "expectedScenarioMatch": "mismatch",
  "expectedAction": "limit_background_activity",
  "evidenceLevel": "E4",
  "kind": "scenario_mismatch"
}
```

```json
{
  "id": "S-002",
  "appScene": "map",
  "eventType": "location",
  "foregroundState": "foreground",
  "expectedCategory": "necessary",
  "expectedRiskLevel": "low",
  "expectedScenarioMatch": "match",
  "expectedAction": "none",
  "evidenceLevel": "E4",
  "kind": "normal"
}
```

## 2. v0.1 fixture 预期结果旁路格式

阶段 1 的固定事件样例由 [fixtures/privacy-events-v0.1.json](fixtures/privacy-events-v0.1.json) 提供；对应预期结果由 [fixtures/privacy-events-v0.1.expected.json](fixtures/privacy-events-v0.1.expected.json) 提供。

`privacy-events-v0.1.expected.json` 是**评测/测试旁路数据**，不属于 `PrivacyEvent` 正式事件契约，不得把 `expectedRiskLevel`、`expectedMatchedRules` 等预期字段写入事件 JSON 或事件库。正式事件字段仍以 [09 事件契约](09-event-contract.md) 为准。

该旁路文件使用以下最小结构：

- 顶层 `schema`：说明旁路文件名称、用途、对应事件 fixture 和 `ruleVersion`；
- 顶层 `expectations`：预期结果数组；
- 每条预期通过 `eventId` 与事件 fixture 中的唯一事件一对一关联；
- 每条预期至少表达：预期分类、预期风险等级、预期场景匹配、预期置信度、预期命中规则、预期处置、是否展示 `unknown` 降级、样例类型、Demo 与真实等价关系、简短依据。

验证规则：

- 每个事件必须恰好对应一条预期项；
- 不允许引用不存在的 `eventId`；
- 预期规则 ID、风险等级、场景匹配、置信度和分类必须遵循 [10 风险规则契约](10-risk-rule-contract.md)；
- `unknown` 事件不得生成确定性 App 归因、确定性数据流向结论或确定性处置建议；
- Demo 事件必须说明其预期是否按等价真实场景评测，并在界面和解释中保留 Demo/沙箱标识；
- 预期依据不得使用“窃取”“恶意上传”“已泄露”等超出证据的措辞，解释层仍遵循 [11 AI 解释契约](11-ai-explanation-contract.md)。

该旁路格式用于 Fake Repository、规则引擎、证据链、解释页面和评测脚本共享同一组预期，不改变采集层事件格式。

## 3. 分布建议

| 类别 | 比例 |
|---|---|
| 正常行为 | 30% |
| 异常行为 | 35% |
| 场景不匹配 | 25% |
| 无法确认的边界 | 10% |

## 4. 评测流程

对每条样例分别运行规则引擎与 AI 摘要，记录：

- 实际分类、风险等级、场景一致性；
- AI 摘要是否与输入事实一致（事实一致率）；
- 是否误报/漏报/无法确认。

## 5. 重点指标

| 指标 | 定义 |
|---|---|
| 数据流向分类准确率 | 正确分类数 / 总数（必要/分析/高风险） |
| 分类召回率 | 检出 / 应检出 |
| 高风险识别召回率 | 高风险事件被标记的比例 |
| 场景一致性准确率 | match/mismatch 判断正确比例 |
| AI 事实一致率 | AI 输出事实与输入一致的比例 |
| 用户处置耗时 | 完成一次处置所需时间 |
| 处置前后频率变化 | 同类事件处置前后对比 |

## 6. 失败案例

- 主动保留误报、漏报、无法确认的样例；
- 每个失败案例记录：期望、实际、可能原因、改进方向；
- 正式文档必须同时报告测试集构成、测试方法和失败案例，不能只展示最好结果。

## 7. 评测运行与版本

- 记录使用的规则版本（`rule_version`）与模型版本，保证可复现；
- 输出评测表 / 脚本与指标图表；
- 评测结果与失败案例写入正式设计文档。

## 8. 验收检查

- [ ] 评测集含正常、异常、无法确认边界三型样例；
- [ ] 每条有预期分类、风险等级、证据来源和建议动作；
- [ ] 能复现的测试方法，而非只看截图；
- [ ] 失败案例被保留并分析。
