# B5-7 处置前后评测样例

> 状态：已完成
> 实现：`rule-engine/src/test/kotlin/com/causalguard/rules/RecheckEvaluationDatasetTest.kt`
> 资产：`docs/fixtures/recheck-cases-v0.1.json`（输入）、`docs/fixtures/recheck-expected-v0.1.json`（oracle）

## 目标

为 [RecheckComparator](b5-5-recheck-comparator.md) 建立可复现的评测集：给定一条处置记录（含处置前快照）和处置后观察，断言确定性比较结果 `reduced / no_change / blocked / unknown`。本批含 **12 条**样例，覆盖全部四类结论及边界降级。

## 资产隔离

按 [14 评测数据集方案](14-evaluation-dataset.md) 第 2 节，输入与 oracle 严格分离：

- `recheck-cases-v0.1.json` 只含 `caseId`、`record`（`MitigationRecord`）、`preObservation`/`rawPreSnapshot`（处置前 `NetworkObservation`）和 `postObservation`；
- `recheck-expected-v0.1.json` 只含 `caseId`、`kind`、`expectedOutcome` 和 `rationale`；
- 输入文件不得出现 `expectedOutcome`、`kind`、`rationale` 等输出字段，回归测试会遍历 JSON 强制校验；
- `kind`、`expectedOutcome` 属于输出 oracle，永不参与构造输入。

回归按 `caseId` 将输入与 oracle 一一关联，`caseId` 唯一且不需引用不存在的记录。处置前快照由测试机械编码：有 `preObservation` 时以 `ContractJson` 序列化为 `MitigationRecord.preSnapshot`；`rawPreSnapshot` 用于注入 malformed 字符串以验证降级不抛异常。

## 覆盖矩阵

| caseId | 场景 | 期望 `expectedOutcome` |
|---|---|---|
| recheck-case-0001 | 前窗允许 10，后窗无请求 | `reduced` |
| recheck-case-0002 | 前窗允许 10，后窗 6 请求 2 阻断（允许 4） | `reduced` |
| recheck-case-0003 | 前后允许连接数相同（10） | `no_change` |
| recheck-case-0004 | 前后均 `ALL_BLOCKED` | `no_change` |
| recheck-case-0005 | 前窗非全阻断，后窗全部被阻断 | `blocked` |
| recheck-case-0006 | `executionStatus=unavailable` | `unknown` |
| recheck-case-0007 | `action=open_settings` | `unknown` |
| recheck-case-0008 | 前窗允许基线为 0 | `unknown` |
| recheck-case-0009 | 后窗允许连接数增加（3→7） | `unknown` |
| recheck-case-0010 | 前后窗口长度不等 | `unknown` |
| recheck-case-0011 | target/pre/post 域名大小写与空格归一化 | `reduced` |
| recheck-case-0012 | 处置前快照为 malformed JSON | `unknown` |

## 回归断言

`RecheckEvaluationDatasetTest` 对每条样例：

1. 运行 `RecheckComparator.compare(record, postObservation)`；
2. 断言 `expectedOutcome == result.postResultWire`，并校验 `postResultWire` 与内部 `RecheckOutcome` 映射一致；
3. 断言 `reviewNotes` 非空，且相同输入两次比较结果相等（确定性）；
4. 校验输入无 oracle 字段、`caseId` 唯一、样例数 ≥ 8；
5. 汇总并打印各 outcome 计数，要求 `reduced/no_change/blocked/unknown` 四类均出现。

## 指标

- **处置前后频率变化**：`allowedCount` 前后对比（[14 §5](14-evaluation-dataset.md)）；
- **结论分布**：四类 outcome 的样例计数，确保覆盖“减少、无变化、被阻断、无法确认”；
- **诚实降级率**：非 executed、非 `block_domain`、坏快照、不可比窗口必须落入 `unknown`。

## 边界

评测只验证确定性比较逻辑，不运行 AI、不读 Repository、不访问 Android API，也不声称处置导致行为变化。`unknown` 表示无法确认，绝不包装为成功；文案中不出现“处置导致”“证明生效”“风险已消除”等因果升级措辞。
