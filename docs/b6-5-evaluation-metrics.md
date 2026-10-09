# B6-5 Evaluation Metrics

> 状态：实现候选，待正常 WSL 完成 `:rule-engine:testDebugUnitTest` 验证。
> 本文只报告可由固定资产复现的统计，不把 curated regression 包装成真实世界准确率。

## 1. 数据集与 ground truth

规则评测分为两个完全隔离的 `ruleVersion`：

| 版本 | 数据 | case 数 | 用途 |
|---|---|---:|---|
| `rules-v0.1` | frozen 10 + extension 15 | 25 | historical regression |
| `rules-v0.2` | boundary dataset | 17 | current runtime boundary |

事件、独立 context 和 expected 文件分别加载。测试真实构造 `RuleInput` 并调用 `RuleEvaluator`；expected 文件只作为项目人工/契约定义的 oracle，不进入输入。42 条合计只能称为 `cross-version regression health`，不能称为当前规则版本的准确率。

## 2. 规则指标

```text
category exact-match = count(actual.category == expected.category) / all cases
risk exact-match = count(actual.riskLevel == expected.riskLevel) / all cases
scenario exact-match = count(actual.scenarioMatch == expected.scenarioMatch) / all cases

recall(c) = count(expected.category == c && actual.category == c)
            / count(expected.category == c)

macro recall = mean(recall(c) for categories with support > 0)

high-risk severity recall = count(expectedRisk in {high, critical}
                                  && actualRisk in {high, critical})
                           / count(expectedRisk in {high, critical})
```

`unknown` 是合法分类和场景值，不能从分母中删除。`expectedCategory == high_risk` 是 category recall，不替代 severity-based high-risk recall。

固定 support 与预期结果：

| 版本 | category support | category exact-match | risk exact-match | scenario exact-match | high-risk severity recall |
|---|---|---:|---:|---:|---:|
| `rules-v0.1` | necessary 4, analytics 3, high_risk 12, unknown 6 | 25/25 | 25/25 | 25/25 | 9/9 |
| `rules-v0.2` | necessary 0, analytics 1, high_risk 8, unknown 8 | 17/17 | 17/17 | 17/17 | 6/6 |

当前所有有 support 的类别 recall 应为 100%；`rules-v0.2` 的 necessary recall 是 `N/A`，macro recall 只平均 analytics、high_risk、unknown 三类。

如果测试全部通过，正确表述是：

> 在固定 `rules-v0.1` / `rules-v0.2` curated evaluation set 上，RuleEvaluator 与项目定义 oracle 达到 100% deterministic exact-match agreement。

这不等价于真实世界准确率、泛化准确率或对所有 Android App 的准确率。

## 3. Synthetic AI guardrail

输入资产为 `ai-explanation-evaluation-cases-v0.1.json`，独立 oracle 为 `ai-explanation-evaluation-expected-v0.1.json`。共 12 条，不联网、不调用 Retrofit 或真实模型。每条 request 是完整 `AiExplanationRequest` 白名单 DTO，candidate 是 `ExplanationText`；输入文件不包含 expected disposition、violation type 或 rationale。

validator 结果映射为：

```text
Accepted(sanitized=false) -> ACCEPT
Accepted(sanitized=true)  -> SANITIZE
Rejected                  -> REJECT
```

样例覆盖合法输出、匹配高风险文字、缺少风险文字、action 中的“检查权限”、禁止措辞、额外数字、额外 package、事实字段新增事件类型、风险等级冲突、空段和 package-like token。预期分布为 `ACCEPT=4`、`SANITIZE=1`、`REJECT=7`，但最终数值以测试实际运行结果为准。

该指标只能称 `synthetic AI guardrail disposition agreement` 或 `synthetic validator disposition agreement`。真实 AI fact consistency 为 `N/A`，因为当前没有固定真实模型/version 输出快照，也没有独立人工事实标注。

## 4. Recheck outcome 与频率变化

复用既有 12 条 `recheck-cases-v0.1.json` 和 `recheck-expected-v0.1.json`，真实调用 `RecheckComparator`。预期 outcome 分布：

| outcome | case 数 |
|---|---:|
| `reduced` | 3 |
| `no_change` | 2 |
| `blocked` | 1 |
| `unknown` | 6 |

因此可报告 `12/12 synthetic recheck outcome oracle agreement`，但这不是现实设备性能指标。

只对 comparator 返回 `REDUCED`、`NO_CHANGE` 或 `BLOCKED` 的 6 条 confirmed case 计算：

```text
absoluteDelta = postAllowedCount - preAllowedCount
percentageChange = absoluteDelta / preAllowedCount * 100
```

当 `preAllowedCount == 0` 时 percentage 为 `N/A`。结果如下：

| case | pre → post allowed | delta | change |
|---|---:|---:|---:|
| 0001 | 10 → 0 | -10 | -100% |
| 0002 | 10 → 4 | -6 | -60% |
| 0003 | 10 → 10 | 0 | 0% |
| 0004 | 0 → 0 | 0 | N/A |
| 0005 | 4 → 0 | -4 | -100% |
| 0011 | 10 → 4 | -6 | -60% |

`BLOCKED` 不改名为 `REDUCED`；unknown、未执行、`OPEN_SETTINGS`、不等长窗口、坏快照和 `pre=0` 不进入 confirmed frequency percentage 分母。这里的名称是 `synthetic recheck allowed-count change`，不能写成真实用户网络流量下降比例。

## 5. 用户处置耗时

当前没有真实 user-start 或 user-complete 时间戳，因此用户处置耗时为 `N/A`。以下字段供后续成员 A 的真人/真机测量协议使用：

- `sampleId`
- `actionType`
- `targetType`
- `userActionStartedAt`
- `userActionCompletedAt`
- `successful` / `aborted`
- `durationSeconds`
- `device/system`
- `notes`

```text
durationSeconds = userActionCompletedAt - userActionStartedAt
```

不得使用 `observationEnd - executedAt`、`observationEnd - observationStart` 或 recheck 等待时间冒充用户处置耗时。本轮 B 不做真机测量。

## 6. 局限与 B6-6 边界

输入、context、oracle 在结构上分离，测试拒绝把 output oracle 字段写入 context 或 synthetic AI input。但 oracle 是人工/契约定义的 curated 标签，不是 external blind ground truth，因此存在 oracle circularity 限制。

B6-5 只负责确定性统计和诚实报告。后续发现 false positive、false negative、disagreement 或 ambiguous unknown 时只记录案例，不为了提高分数修改规则；规则校准留给 B6-6。
