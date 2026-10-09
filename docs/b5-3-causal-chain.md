# B5-3 因果链节点和事实/推断等级

> 状态：已完成
> 实现：`rule-engine/src/main/kotlin/com/causalguard/rules/CausalChainBuilder.kt`

## 语义边界

本项目中的“因果链”准确含义是 evidence-supported explanation chain：它说明哪些事件事实支持哪些派生判断，不构成科学或法律意义上的因果证明。`supports` 不等于 `causes`；链不表达“导致了泄露”“因此上传”“因此窃取”或“因此发送了敏感数据”。

B5-3 只消费 B5-2 已确定的 `RiskAssessment`、`EvidenceLink` 和降级结果，不重新匹配规则、不重新选择 witness、不修改原始事件，也不接入 UI、AI 或 Room 持久化。

## 节点与证据等级

内部节点类型固定为：

- `EVENT_EVIDENCE`：来自 `RiskAssessment.evidenceIds` 的原始 `PrivacyEvent`。节点原样保留事件的 E1/E2/E4/E5 等级；主事件始终存在。
- `TEMPORAL_INFERENCE`：对应 B5-2 的 `relation=temporal`，固定为 E3。描述沿用 link 边界，只表示时间/先后关联。
- `RULE_INFERENCE`：对应 B5-2 的 `relation=rule`，固定为 E3。规则命中是 Derived Inference，不是 System Fact 或 VPN Observed Fact。
- `ASSESSMENT`：代表 canonical `RiskAssessment`。正常派生评估为 E3；`category=unknown` 或 unknown degradation 的最终“无法确认”结果为 E5。

事件事实等级不会因为命中规则而升级：例如 E2 VPN 观测和 E4 Demo 事件仍保持各自原等级，E5 unknown 事件也不会变成确定事实。

## DAG 结构

节点 ID 不使用 UUID、当前时间或对象 hash：

- `event:<eventId>`
- `temporal:<primaryEventId>:<linkedEventId>:<ruleId>`
- `rule:<primaryEventId>:<ruleId>`
- `assessment:<assessmentId>`

所有边只有 `relation=supports`：

- supporting event 与 primary event 分别支持对应 temporal inference；
- primary event 支持 rule inference；有 temporal link 时 temporal inference 继续支持对应 rule inference；
- 每个 rule inference 支持最终 assessment；无 rule node 时 primary event 直接支持 assessment。

不会生成 event-to-event 的 `causes` 边。R-007 的剪贴板与网络事件只能形成时间关联，不能证明剪贴板内容被发送或发生泄露。R-009 的权限事件与 Demo 位置事件只能表达 synthetic/Demo prior context，不能声称真实 Android 权限被绕过。

## 降级与完整性

no-match 和 version mismatch 都只生成 primary `EVENT_EVIDENCE` 与 E5 `ASSESSMENT`，不生成 temporal/rule node。unknown 事件可以保留 E3 的 R-008/R-010 rule inference，但最终 assessment 仍为 E5，并继续表达无法确认/无法可靠归属。

builder 对 primary、证据事件、link relation、rule link、linked event、节点/边唯一性和 DAG 结构执行 fail-fast 校验；不为缺失或内部不一致的数据编造 unknown 节点。链顺序和所有 ID 均 deterministic，可由同一输入重建。

scene knowledge 的 `SCENE-*` inference 本轮不物化为因果链节点；B5-1 的 scene evaluator 已在 runtime 作为 `ScenarioMatch` 输入接入（见 [B5-1](b5-1-scene-knowledge.md)），但因果链仍只消费规则评估结果，不直接展开场景知识节点。后续任务可以在明确集成边界后再扩展。
