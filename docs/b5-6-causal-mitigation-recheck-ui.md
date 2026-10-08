# B5-6 因果链、处置和复查页面

> 状态：开发中

## 页面数据流

详情页只消费一次规则评估产生的结果：

```text
RuleEvaluationResult
  -> EventAnalysisResult
  -> EventDetailViewModel
  -> EventDetailScreen
```

`EventAnalysisResult` 同时携带 `causalChain` 和 canonical `recommendationSelection`。页面不重新运行 `RuleEvaluator`、`CausalChainBuilder` 或 `RecommendationSelector`。

## 证据支持链

页面按稳定顺序展示事件事实、时间推断、规则推断和最终评估节点。边只展示 `supports / 支持来源`，不展示 raw node ID，也不把 supports 改写为 causes。该链表示事实支持哪些推断，不构成敏感数据传输或泄露的因果证明。

证据等级保持冻结语义：E1 System Fact、E2 Observed Fact、E3 Derived Inference、E4 Demo Ground Truth、E5 Unable to Confirm / Unknown。E4 不表示 Android 系统事实。

## 建议与执行

页面主展示使用 `recommendationSelection.recommendation`，包括 title、reason、expectedImpact、systemPath、evidenceIds 和 reversible。Recommendation 是建议，不代表已执行；`mitigationRequest` 为空时不显示执行按钮，并显示当前没有可安全执行的自动操作。

只有用户主动点击执行按钮后，调用链才是：

```text
RecommendationSelection
  -> user click
  -> MitigationExecutor
  -> MitigationRecord
```

页面加载不会自动执行动作，也不会自动打开系统设置。`EXECUTED` 只表示执行层确认了动作边界：`OPEN_SETTINGS` 表示设置页已打开，不表示用户改变了设置；`BLOCK_DOMAIN` 表示底座确认接受阻断请求，仍需观察和复查。FAILED、UNAVAILABLE、UNSUPPORTED 均原样展示为非成功状态。

## 复查流

```text
observation window
  -> NetworkObservationRepository
  -> RecheckComparator
  -> MitigationRepository.updateOutcome
  -> EventDetailScreen
```

只有 `BLOCK_DOMAIN + executed + observationEnd` 且窗口已结束时，用户才能手动复查。post observation 严格查询 `[executedAt, observationEnd]`，Comparator 负责全部 REDUCED、BLOCKED、NO_CHANGE、UNCONFIRMABLE 判定，页面不复制算法。

窗口未结束时显示“观察中”，不提前查询。`unknown` 且没有 `reviewNotes` 表示等待复查；`unknown` 且有 `reviewNotes` 表示已复查但无法确认。页面展示 comparator 的审计说明，不用更强文案覆盖它。

页面不展示原始 `preSnapshot` JSON。`supports != causes`，时间关联、规则推断和复查观察都不升级为敏感数据上传、泄露、权限绕过或风险消失的证明。
