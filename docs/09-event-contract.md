# 09 事件契约（采集 → 事件库）

> 版本：`v0.1`（P0 设计基线）
> 最后更新：2026-09-24
> 责任人：成员 A
> 状态：**已冻结（阶段 2，2026-09-24）**。字段与枚举变更必须走独立契约 PR，并同步 [07 数据模型](07-data-model.md)、[08 表结构](08-database-schema.sql)、fixture 与验收清单。
> 目的：冻结采集模块写入事件库的格式，使页面和规则无需真实 VPN 即可被固定 JSON 驱动。

## 0. 契约版本（`schemaVersion`）

- 当前 `schemaVersion = "0.1"`，以字符串 `主版本.次版本` 表示；
- 每条事件必须携带该字段，采集写入与 JSON fixture 一致；
- 不兼容字段变更时递增次版本，并在本文件记录变更说明；
- Kotlin 侧对应 `com.causalguard.core.model.SchemaVersion.CURRENT`（见 `core-model` 模块）。

## 1. 统一事件 JSON

所有采集器（真实/演示）输出以下结构，写入 `privacy_event`：

```json
{
  "schemaVersion": "0.1",
  "eventId": "e-20260920-0001",
  "appId": "com.example.calculator",
  "appName": "计算器",
  "eventType": "clipboard",
  "timestamp": 1789000000000,
  "foregroundState": "background",
  "source": "demo",
  "evidenceLevel": "E4",
  "evidenceSummary": "后台读取剪贴板，长度 6，未保存原文",
  "category": "unknown",
  "isDemo": true,
  "dedupKey": "com.example.calculator|clipboard|background|1789000000"
}
```

说明：

- `category`、`riskScore`、`confidence`、`explanation` 由规则引擎回填，采集层可先留空/默认。
- `evidenceSummary` 不得包含敏感原文，只允许摘要、长度、哈希。
- `source` 枚举：`system_api`、`usage_stats`、`vpn`、`demo`、`mock`。
- `evidenceLevel` 枚举：`E1`~`E5`（定义见 [02-android-capability-matrix.md](02-android-capability-matrix.md)）。
- `foregroundState` 枚举：`foreground`、`background`、`recent`、`unused`、`unknown`。

### 1.1 公共枚举（冻结）

JSON 值统一使用小写 snake_case；Kotlin 侧对应 `core-model` 中枚举，未知值映射为 `UNKNOWN`，不得抛异常。

| 字段 | Kotlin 枚举 | 允许值 |
|---|---|---|
| `eventType` | `EventType` | `clipboard`、`location`、`contacts`、`network`、`usage_context`、`permission`、`unknown` |
| `foregroundState` | `ForegroundState` | `foreground`、`background`、`recent`、`unused`、`unknown` |
| `source` | `EventSource` | `system_api`、`usage_stats`、`vpn`、`demo`、`mock`、`unknown` |
| `evidenceLevel` | `EvidenceLevel` | `E1`、`E2`、`E3`、`E4`、`E5` |
| `category` | `RiskCategory` | `necessary`、`analytics`、`high_risk`、`unknown` |
| `confidence` | `Confidence` | `low`、`medium`、`high` |
| `protocol` | `NetworkProtocol` | `TCP`、`UDP`、`ICMP`、`unknown` |

> 新增枚举值必须同步本表、`docs/07`、`docs/10` 与 `core-model`，否则视为契约破坏。

## 2. 各类型事件附加结构

### 2.1 network（附 `network_event`）

```json
{
  "eventType": "network",
  "source": "vpn",
  "evidenceLevel": "E2",
  "network": {
    "protocol": "TCP",
    "remoteIp": "203.0.113.10",
    "remotePort": 443,
    "domainHint": "analytics.example.com",
    "uid": 10123,
    "packageName": "com.example.calculator",
    "bytesIn": 1024,
    "bytesOut": 256,
    "blocked": false
  }
}
```

约束：`domainHint` 可为空；`packageName` 无法归属时为 `unknown`，`uid` 为 -1。

> P0 标注：底座（TrackerControl/NetGuard）的 `Packet` 只提供连接元数据，不含字节数。`bytesIn`/`bytesOut` 在阶段 1 Spike 阶段允许为 `0` 或 `null`；待 P1 从 Usage 统计补齐后再启用。静态依据见 [network-core-map](network-core-map.md) 第 8 节。

### 2.2 usage_context（附 `usage_context_event`）

```json
{
  "eventType": "usage_context",
  "source": "usage_stats",
  "evidenceLevel": "E2",
  "usage": {
    "packageName": "com.example.calculator",
    "state": "background",
    "screenOn": true
  }
}
```

### 2.3 clipboard / location / contacts（Demo 真值）

```json
{
  "eventType": "location",
  "source": "demo",
  "evidenceLevel": "E4",
  "isDemo": true,
  "evidenceSummary": "后台延迟访问位置，调用 2 次，未保存坐标"
}
```

### 2.4 permission（系统事实）

```json
{
  "eventType": "permission",
  "source": "system_api",
  "evidenceLevel": "E1",
  "evidenceSummary": "位置权限状态由 granted 变为 revoked"
}
```

## 3. 写入接口契约

```kotlin
interface EventSink {
    suspend fun emit(event: ContractEvent)
    suspend fun emitAll(events: List<ContractEvent>)
}
```

- 唯一写入口：统一事件库（M4）。
- 写入需幂等：相同 `eventId` 不重复插入。
- `dedupKey` 用于频率聚合时合并，不改变原始事件留存。

## 4. 去重与频率聚合

- 时间窗口：默认 60 秒（可配置）。
- 窗口内相同 `dedupKey` 的事件合并为一条并累加计数。
- 聚合结果只用于告警频率判断，不删除原始事件。

## 5. 契约测试

- [ ] 用固定 JSON 注入 4 类事件，页面与规则无需 VPN 即可运行；
- [ ] 无敏感原文；
- [ ] 演示事件 `isDemo=true`；
- [ ] 相同 `eventId` 幂等；
- [ ] 无法归属网络事件显示 `unknown`。
