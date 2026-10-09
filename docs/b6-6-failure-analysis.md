# B6-6 Failure Analysis and Calibration Boundary

> 状态：已验证；`:rule-engine:testDebugUnitTest` 已在正常 WSL 通过。
> 本文保存 curated evaluation 的 disagreement/unknown 分类，不把 oracle 结果宣传为现实世界误报率或漏报率。

## 1. Evaluation scope

failure analysis 覆盖三组规则评测资产：`rules-v0.1` frozen 10 条、`rules-v0.1` extension 15 条、`rules-v0.2` current boundary 17 条，共 42 条。test-only `FailureCaseAnalysisTest` 会真实加载规则资产、构造 `RuleInput`、调用 `RuleEvaluator`，再与各自独立 oracle 对比。

机器可读 registry 为 [evaluation-failure-cases-v0.1.json](fixtures/evaluation-failure-cases-v0.1.json)。registry 只引用 `caseId`/`eventId`，不复制完整事件或敏感内容，并由测试校验 case 唯一性、dataset、ruleVersion、类型和 oracle 字段。

## 2. Failure classification

- `false_positive`：actual risk rank 高于 expected，属于 curated oracle 下的 severity over-escalation。
- `false_negative`：actual risk rank 低于 expected，属于 curated oracle 下的 severity under-escalation。
- `classification_disagreement`：risk severity 相同，但 category 或 scenarioMatch 不同。
- `unknown_boundary`：oracle `kind=unknown_boundary` 的显式安全降级边界。
- `context_only_unknown`：expected category/scenario 为 unknown，但不是显式 unknown boundary 的上下文事实。

unknown 不自动称为 false negative；未知归属、缺少场景、E5 证据不足或权限上下文事实都可能是有意的安全降级。

## 3. Observed baseline

在当前固定 curated oracle evaluation set 中，动态 analyzer 的目标基线为：

| ruleVersion / dataset | cases | false positive | false negative | classification disagreement | unknown boundary | context-only unknown |
|---|---:|---:|---:|---:|---:|---:|
| `rules-v0.1` historical | 25 | 0 | 0 | 0 | 2 | 4 |
| `rules-v0.2` boundary | 17 | 0 | 0 | 0 | 6 | 2 |
| total | 42 | 0 | 0 | 0 | 8 | 6 |

这些数量来自现有 fixture 的 expected kind/category/scenario 结构，并由测试通过真实 evaluator 结果防止 registry 漂移。正确表述是：

> 在当前 fixed curated oracle evaluation set 中未观察到 severity over/under disagreement。

不能写成“系统无误报”“系统无漏报”“误报率 0%”或“漏报率 0%”。

## 4. Calibration candidates

### R-006

契约概念描述为 `lastUsedAgoMs` 超阈值加后台网络；当前 `risk-rules-v0.2` 资产和 `RuleEvaluator` 实际只消费 `foregroundState=unused`，不直接读取 `RuleInput.usageContext.lastUsedAgoMs`。这是 upstream 离散化依赖造成的 semantic/contract gap，不是当前 oracle disagreement。本轮不扩展 `RuleCondition`，不发明阈值，也不修改 R-006。

### R-008

`rules-v0.2` 的 contract 语义是 `network.packageName=unknown` 即 unknown attribution，已知 UID 不代表已知 App；UID 为 `-1` 不再是独立必要条件。冻结的 `rules-v0.1` 保留 package unknown + UID -1 的历史联合条件。`docs/10` 已做最小版本化说明，生产规则未修改。

### R-009

当前规则要求同 App 更早的 revoked permission prior event，但没有 `timeWindowMs`。这是 potential over-broad calibration candidate；现有契约没有定义时间阈值，不能自行加入 1 小时、24 小时或其他窗口。后续需要独立标注的 long-gap cases 后再决定。

## 5. Calibration decision

本轮 production calibration decision：**NO_CHANGE**。

原因是当前没有 independent oracle disagreement，也没有由现有产品契约明确规定、无需发明新事实或新阈值的生产规则错误。`risk-rules-v0.1` frozen asset、`risk-rules-v0.2`、`RuleEvaluator` 和 `RuleCondition` 均未修改。

## 6. B6-6 后续边界

如果未来新增真实/独立标注并发现 disagreement，只有在同时满足以下条件时才进入规则校准：

1. 有 independent evidence 或 blind label；
2. 能明确指出当前规则逻辑错误；
3. 修复语义已由产品契约定义；
4. 不需要臆造新的阈值或事实；
5. 不是为了单纯提高 curated score。

否则继续登记为 failure/unknown/candidate，不修改生产规则。
