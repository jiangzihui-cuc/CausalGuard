# B5-5 RecheckComparator

> 状态：已完成
> 实现：`rule-engine/src/main/kotlin/com/causalguard/rules/RecheckComparator.kt`

## 职责边界

`RecheckComparator` 是纯确定性比较器，只消费 `MitigationRecord`、记录中的 `preSnapshot` 和调用者提供的 `postObservation`。它不读取 Repository、不写 `MitigationRepository.updateOutcome`、不调用 `MitigationExecutor`、不访问 Android API，也不读取当前时间。调用者负责查询 post observation，再将结果写回持久化层。

`preSnapshot` 使用既有 `ContractJson` 按 `NetworkObservation` 反序列化；缺失、空白或 malformed JSON 都返回 `UNCONFIRMABLE`，不会抛出异常。

## Outcome 与 persistence wire

| `RecheckOutcome` | `MitigationRecord.postResult` wire | 语义 |
|---|---|---|
| `REDUCED` | `reduced` | 观察到允许通过的连接数下降 |
| `NO_CHANGE` | `no_change` | 可比窗口中允许通过的连接数相同，或前后均已全部阻断 |
| `BLOCKED` | `blocked` | 后窗口仍有连接尝试，且全部被标记为 blocked |
| `UNCONFIRMABLE` | `unknown` | 执行、证据、窗口、基线或数据质量不足，无法可靠确认 |

`RecheckResult` 同时提供 `outcome`、`postResultWire`、可审计 `reviewNotes`、解析后的 `preObservation` 和输入的 `postObservation`。

## 比较前提

只有 `executionStatus=executed` 且 `action=block_domain` 才进入网络复查。`failed`、`unavailable`、`unsupported`、`unknown` 和其他 action 均返回 `UNCONFIRMABLE`。记录必须有非空 target、合法的 `executedAt`/`observationEnd`，并满足 `observationEnd > executedAt`。

前窗口必须是 `[executedAt-window, executedAt]`，后窗口必须是 `[executedAt, observationEnd]`；两窗口都必须为正长度且等长。前后 package 必须等于记录 package，target、pre domain、post domain 必须是同一个非空域名，比较时只做 trim 和大小写归一化。请求数、阻断数必须非负且 `blockedCount <= requestCount`。

比较指标是 `allowedCount = requestCount - blockedCount`，不是单独比较 requestCount。`NO_REQUEST` 与 `ALL_BLOCKED` 保持区分：前者表示没有观察到连接尝试，后者表示有尝试但全部被阻断。

## 结果边界

- `post=ALL_BLOCKED` 且 `pre!=ALL_BLOCKED`：`BLOCKED`；文案只描述后窗口观察事实，不声称处置导致阻断。
- `pre=ALL_BLOCKED` 且 `post=ALL_BLOCKED`：`NO_CHANGE`，避免把已有阻断状态包装成新的改善。
- 非 BLOCKED 情况下，`pre.allowedCount > 0` 且 post 更低：`REDUCED`，包括 post 为 `NO_REQUEST`。
- 非 BLOCKED 情况下，前后 allowedCount 相同：`NO_CHANGE`。
- pre allowed baseline 为 0（未命中前述全部阻断规则）或 post allowedCount 增加：`UNCONFIRMABLE`。
- `SOME_BLOCKED` 本身不等于 `BLOCKED`，仍按 allowedCount 比较。
- `OPEN_SETTINGS` 即使执行成功，也不能由 NetworkObservation 证明权限或后台策略发生变化，因此返回 `UNCONFIRMABLE`；`BLOCK_APP`、`NONE` 和未知 action 同理。

所有 review notes 只陈述观察事实、校验失败原因或无法确认边界，不使用“处置导致”“证明生效”“风险已消除”等因果升级文案。
