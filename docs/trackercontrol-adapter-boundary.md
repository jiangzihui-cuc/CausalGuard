# TrackerControl Adapter 边界（阶段 2 冻结）

> 版本：`v0.1`
> 最后更新：2026-09-24
> 责任人：成员 A
> 对应任务：A2-5（冻结 TrackerControl Adapter 边界）
> 上游底座：TrackerControl Android，tag `2026080501`，commit `9504d41b9f6fa1509d784e5503c084d4b428307d`（GPL-3.0）
> 关联：[network-core-map](network-core-map.md)、[09 事件契约](09-event-contract.md)、[02 能力边界表](02-android-capability-matrix.md)、[06 模块设计](06-module-design.md) M3

---

## 1. 目标

把 TrackerControl/NetGuard 底座与我们的业务代码之间切成一条**稳定、可回滚、可替换**的边界：

- 底座内部（native 核心、`ServiceSinkhole`）尽量不改，只做最小回调挂接；
- 业务侧（规则、页面、Room）**只依赖 `core-model` 的接口与 `NetworkEvent`**，不碰底座类；
- 一旦底座换 tag 或改用 MIT 备选，只需重写 Adapter，不动规则与 UI。

边界示意：

```text
[TrackerControl/NetGuard 底座]                      [CausalGuard]
 native + ServiceSinkhole
   logPacket / dnsResolved / getUidQ /           NetworkEventSource
   isAddressAllowed  ─── Adapter 挂接 ─────────►  (core-model 接口)
                                                        │
                                                        ▼
                                             EventSink → 事件库 / 规则 / UI
```

---

## 2. 输入边界：底座提供什么

只有以下 4 个 Java 回调（详见 [network-core-map](network-core-map.md) 第 1、4、5、6、7 节）：

| 回调 | 提供 | 用途 |
|---|---|---|
| `logPacket(Packet)` | `time`、`protocol`、`saddr/sport`、`daddr/dport`、`uid`、`allowed` | 连接事件主源 |
| `dnsResolved(ResourceRecord)` | `QName`、`AName`、`Resource`(IP)、`TTL` | `domainHint` |
| `getUidQ(...)` | 五元组 → UID（仅 TCP/UDP） | `uid` 归属 |
| `isAddressAllowed(Packet)` | 放行/阻断判定 | `blocked` |

**底座不提供**：请求体/响应体、TLS 明文、精确字节数（`Packet` 不含 `bytesIn/bytesOut`）。字节数走 `Usage`/DB 统计，P0 置 0，P1 再补。

---

## 3. 输出边界：Adapter 只产出 `NetworkEvent`

Adapter 把回调转换为契约对象（[09](09-event-contract.md) 2.1 节）：

| 契约字段 | 来源 | 失败降级 |
|---|---|---|
| `protocol` | `Packet.protocol`：6→`TCP`、17→`UDP`、1/58→`ICMP`、其他→`unknown` | `unknown` |
| `remoteIp` | `Packet.daddr` | 空则丢弃该事件 |
| `remotePort` | `Packet.dport` | — |
| `domainHint` | `dnsResolved` / `ipToHost` 缓存 | 可为空 |
| `uid` | `Packet.uid`（仅底座进程内有效） | `-1` |
| `packageName` | `getPackagesForUid(uid)` | `unknown` |
| `blocked` | `Packet.allowed == false` | `false` |
| `timestamp` | `Packet.time` | — |
| `bytesIn` / `bytesOut` | 不提供 | 固定 `0` |
| `evidenceLevel` | 适配层固定 `E2` | — |
| `source` | 固定 `vpn` | — |
| `isDemo` | 固定 `false` | — |

> `category` / `riskScore` / `explanation` 由规则引擎回填，Adapter **不得**写入结论性分类。

---

## 4. 接口契约（定义在 `core-model`）

```kotlin
package com.causalguard.core.model

interface NetworkEventSource {
    /** 冷/热流由实现决定；Adapter 保证事件已按 docs/09 脱敏。 */
    fun events(): Flow<NetworkEvent>
    suspend fun start()
    suspend fun stop()
}

interface TrackerControlAdapter : NetworkEventSource {
    /** 当前底座是否可用（已授权 VPN 且前台服务存活）。 */
    val isAvailable: Boolean
}

interface EventSink {
    suspend fun emit(event: PrivacyEvent)
    suspend fun emitAll(events: List<PrivacyEvent>)
}
```

约束：

1. Adapter 是唯一允许 import 底座类（`eu.faircode.netguard.*`）的模块（`:app` 的 `network/` 包）；
2. `core-model` 与 `rules` **不得**出现底座类型或 Android 采集 API；
3. 写库统一经 `EventSink`（M4），Adapter 不直接写 Room。

---

## 5. 生命周期与线程

- 挂接点：`ServiceSinkhole` 回调末尾调用 Adapter（[network-core-map](network-core-map.md) 第 9 节方案 1）；
- 回调可能在 native 线程，Adapter 只做轻量转换 + 投递到有界队列，重活交后台；
- `logPacket` 有节流上限（`MAX_QUEUE=1000`），Adapter 需容忍丢包并计数，不阻塞 native；
- VPN 被系统回收/网络切换：`stop()` 后重新 `start()`，不丢历史事件。

---

## 6. 失败与降级

| 情形 | Adapter 行为 | 界面 |
|---|---|---|
| `uid == -1` | `packageName = "unknown"` | “无法归属到具体应用” |
| 无 `dnsResolved` | `domainHint = null` | 只显示 IP/类别 |
| ICMP | `protocol = ICMP`，不参与 UID 成功率分母 | 正常展示 |
| VPN 未授权/已断开 | `isAvailable = false`，`events()` 结束 | “监测已暂停” |
| 底座换 tag/换路线 | 重写 Adapter 实现，规则与 UI 不变 | — |

成功率的量化统计见 [uid-attribution-capture](uid-attribution-capture.md)，结果回填 [02](02-android-capability-matrix.md)。

---

## 7. 明确不做（首版边界）

- 不做 TLS 中间人解密；
- 不读取请求体、剪贴板或账号内容；
- 不修改 native 核心；
- 不把底座 tracker 分类直接当作我们的风险结论；
- 不做 IP-only 阻断（底座基于域名证据，见 network-core-map 第 7 节）。

---

## 8. 变更与许可

- 底座源码首次导入单独 commit，团队在 `ServiceSinkhole` 的挂接改动另起 commit；
- 改动范围与回滚方式记入 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) 与 PR；
- 本边界一旦冻结，字段/接口变更需走独立契约 PR，并同步 [09](09-event-contract.md)、`core-model` 与 `docs/06` M3。
