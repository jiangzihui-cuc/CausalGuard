# B5-2 EvidenceLink 与证据链构建器

> 状态：已完成
> 实现：`rule-engine/src/main/kotlin/com/causalguard/rules/EvidenceChainBuilder.kt`

## 目标与边界

B5-2 将规则评估中真正支持有效规则的事件事实展开为可复查的证据链。主事件仍是根证据；`EvidenceLink` 是事件事实之间或事件事实与规则派生判断之间的关联，不是新的原始观测。

`EvidenceLink` 当前只在规则引擎内构建，不由 B5-2 自动写入 Room。现有 `EvidenceLink` 公共契约、DAO、Entity 和 mapper 均未修改。

## 证据等级

- `PrivacyEvent` 保留原有事实证据等级（E1/E2/E4/E5 等）。
- `temporal` link 使用 E3 Derived Inference，表示时间或先后关联。
- `rule` link 使用 E3 Derived Inference，表示规则基于事件事实得到的派生判断。
- 时间相关不等于因果，不等于敏感内容传输，也不等于数据泄露。
- 规则命中不等于 System Fact、VPN Observed Fact 或 Demo Ground Truth。

## 构建语义

`RuleEvaluator` 先完成现有的 `matched -> unknownMatches -> effectiveMatches` 选择，再把 `effectiveMatches` 交给 `EvidenceChainBuilder`。被 unknown degradation 抑制的高风险规则不会进入证据链。

每次正常评估的 `RiskAssessment.evidenceIds` 都以主事件 ID 开头，之后只包含实际选中的 supporting event ID。ID 去重，supporting event 按 `timestamp` 升序、再按 `eventId` 升序排列；列表顺序不依赖输入集合顺序。`evidenceIds` 是 `PrivacyEvent.eventId`，不是 `EvidenceLink.id`。

### related witness

对于有效规则的 `relatedEventTypes`/`timeWindowMs`，只接受不同于主事件、同一 `appId`、事件类型匹配且处于时间窗内的事件。候选按时间距离绝对值、时间戳、事件 ID 选择最小 witness。

### prior witness

对于每个有效规则的 `requiresPriorEvents`，按事件类型和可选 `evidenceSummaryContains` 匹配 `RuleInput.priorEvents`。每项选择时间戳最近的匹配事件，时间相同按事件 ID 稳定选择。

### links

- 每个选中的 related/prior witness 产生一个 `relation=temporal`、`evidenceLevel=E3` 的 link。
- 每个 effective matched rule 产生一个 `relation=rule`、`evidenceLevel=E3` 的 link。
- `relation=app` 保留在既有契约中，但本版不强制生成；same-app 是 temporal witness 的筛选条件，不重复生成 app link。
- 相同的主事件、linked event、relation、rule ID 组合只保留一个 link。

R-007 的 temporal link 只能说明敏感事件与网络事件在规则时间窗内出现，不能证明剪贴板内容被网络发送。R-009 的 prior link 只表示 synthetic / Demo evaluation context 中存在权限状态上下文，不能证明真实 Android 权限被绕过。

## 降级与后续边界

no-match、规则版本不匹配和没有有效规则 witness 时，不制造假的关联；no-match/version mismatch 只保留主事件且 links 为空。unknown/unavailable 仍表达“无法确认”，不会借证据链补出确定性事实。

B5-1 的 `SceneConsistencyResult.knowledgeRuleId` 本轮不自动物化为 scene knowledge link，也不接入当前 `FixtureEventAnalysisService`。后续 B5-2 之外的 runtime scene integration 或 B5-3 因果链节点可在明确传入 scene inference 时再定义边界。
