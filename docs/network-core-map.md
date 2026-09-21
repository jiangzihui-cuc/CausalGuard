# network-core-map（网络底座核心地图）

> 版本：`v0.1`（阶段 1 Spike 产物）
> 最后更新：2026-09-21
> 责任人：成员 A
> 对应任务：A1-2
> 上游底座：TrackerControl Android
> 固定版本：tag `2026080501`，commit `9504d41b9f6fa1509d784e5503c084d4b428307d`（2026-08-05）
> 许可证：GPL-3.0（见 `THIRD_PARTY_NOTICES.md`）
> 说明：本文件基于固定 commit 的静态源码分析，尚未在设备上验证运行。设备验证结果回填到 `spike-results.md`。

---

## 1. 结论先行

网络能力可以拆成两层，我们的适配层只需接在第二层：

```text
[底层：netguard]  eu.faircode.netguard
  VpnService 生命周期 + TUN 转发 + TCP/UDP/ICMP + DNS 解析 + UID 归属 + 阻断判定
        │  Java 回调（由 native 调用）
        ▼
[上层：missioncontrol]  net.kollnig.missioncontrol
  DnsProxyServer（DoH/DoT）、TrackerBlocklist、BlockingMode、数据与 UI
```

**我们需要插入的点只有 4 个 Java 方法**（native → Java 回调），它们都在 `ServiceSinkhole` 内：

| 回调方法 | 作用 | 对应我们的产物 |
|---|---|---|
| `logPacket(Packet)` | 每条连接尝试的元数据 | 原始连接事件源 |
| `dnsResolved(ResourceRecord)` | DNS 查询→IP→域名映射 | `domainHint` 来源 |
| `getUidQ(int...)` | IP/端口五元组 → UID | `uid` / `packageName` 归属 |
| `isAddressAllowed(Packet)` | 是否放行/阻断 | `blocked` 字段与阻断验证 |

原生核心建议**不修改**（见 `docs/20-open-source-reuse-guide.md` 4.2 节“优先保留 native 核心”）。

---

## 2. 模块与目录对照

| 层 | 路径 | 说明 |
|---|---|---|
| 网络核心（NetGuard） | `app/src/main/java/eu/faircode/netguard/` | 复用，`ServiceSinkhole` 是关键类 |
| 原生核心（JNI/CMake） | `app/src/main/jni/netguard/` + `app/CMakeLists.txt` | `ip.c`/`tcp.c`/`udp.c`/`dns.c`/`session.c` |
| TrackerControl 业务层 | `app/src/main/java/net/kollnig/missioncontrol/` | `vpn/`、`dns/`、`data/`、`analysis/` |
| WireGuard/Rust（可选） | `wgbridge-rs/` + `rust-toolchain.toml` | 可选构建链，P0 不一定走 |

---

## 3. VPN 生命周期与启动入口

核心类：`eu.faircode.netguard.ServiceSinkhole extends VpnService`

| 环节 | 位置 |
|---|---|
| 权限准备 | `VpnService.prepare()`，见 `ServiceSinkhole` 第 562 行附近 |
| 组装 Builder（地址/路由/DNS） | `getBuilder(List<Rule>, List<Rule>)`，第 1538 行 |
| 仅阻断模式 Builder | `getBlockingBuilder(List<Rule>)`，第 1765 行 |
| 建立 TUN | `startVPN(Builder)` → `builder.establish()`，第 1498 行 |
| 启动原生循环 | `startNative(pfd, listAllowed, listRule)`，第 1804 行 |
| 原生循环入口 | `jni_run(context, tun, fwd53, rcode)`，第 270 行声明 |
| 地址/路由 | `builder.addAddress(...)` 第 1593 行；`builder.addRoute(...)` 第 1644 行 |
| DNS 注入 | `builder.addDnsServer(dns)` 第 1607 行 |

要点：

- IPv4 使用 `addAddress(vpn4, 32)` + 路由；IPv6 使用 `2000::/3` 单播路由（第 1716 行）。
- `block_dot` 默认开启：在 `isAddressAllowed` 中阻断 853 端口，防止 DoT 绕过 DNS 过滤（第 2345 行）。
- 默认**不做 TLS MITM**，只做元数据观测，符合项目边界（`docs/01` 第 4 节、`docs/20` 第 12 节）。

---

## 4. 连接事件数据流（A1-5 关键）

```text
native 收到报文 (tcp.c / udp.c / icmp.c)
   → 调用 isAddressAllowed(Packet)         [Java, 判定放行或阻断, 第 2304 行]
   → 调用 logPacket(Packet)                 [Java, 第 2257 行]
      → LogHandler.queue(packet)            [第 848 行, 带节流上限 MAX_QUEUE=1000]
         → LogHandler.log(packet, connection, interactive)   [第 909 行]
            → DatabaseHelper.insertLog(...) [流量日志, 第 990 行]
            → DatabaseHelper.updateAccess(...) [仅 tracker 命中时, 第 998 行]
```

`Packet` 字段（`eu.faircode.netguard.Packet`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `time` | long | 时间戳 |
| `version` | int | IP 版本 |
| `protocol` | int | 1=ICMPv4, 58=ICMPv6, 6=TCP, 17=UDP |
| `flags` | String | TCP flags（如 `S`/`A`） |
| `saddr` / `sport` | String / int | 源地址/端口 |
| `daddr` / `dport` | String / int | 目的地址/端口 |
| `data` | String | SNI 等元数据（默认关闭） |
| `uid` | int | 归属 UID，失败为 `Process.INVALID_UID` |
| `allowed` | boolean | 是否放行 |

> `Size`/`bytesIn`/`bytesOut`：`Packet` 本身不含字节数；字节统计走 `Usage` + `accountUsage(Usage)`（`StatsHandler`，第 1045 行）。我们的事件契约若需要 `bytesIn/bytesOut`，需从 `Usage` 或 `DatabaseHelper` 的统计读取，**这是契约与底座的一处差异**（见第 8 节）。

---

## 5. DNS 映射数据流

```text
native dns.c 解析到响应
   → dnsResolved(ResourceRecord)                       [Java, 第 2213 行]
      → DatabaseHelper.insertDns(rr)                   [第 2234 行]
      → prepareUidIPFilters(rr.QName)                  [第 2236 / 2029 行]
```

`ResourceRecord` 字段：`Time`、`QName`（查询名）、`AName`（CNAME）、`Resource`（解析结果 IP）、`TTL`。

域名来源还包括：

- `ipToHost` / `ipToTracker` 内存缓存（`ConcurrentHashMap`，带 TTL，第 2288 行附近）。
- DoH 代理：`net.kollnig.missioncontrol.dns.DnsProxyServer`、`DnsOverHttpsClient`。
- WireGuard 被动 DNS 映射：`wireGuardDnsResolved(...)`（第 2242 行）。

---

## 6. UID / 包名归属（A1-8 关键）

| 环节 | 位置 |
|---|---|
| native 请求 UID | `get_uid_q(...)` in `netguard.c` 第 832 行 |
| Java 实现 | `getUidQ(version, protocol, saddr, sport, daddr, dport)`，第 2261 行 |
| 系统 API | `ConnectivityManager.getConnectionOwnerUid(protocol, local, remote)`，要求 API 29+ |
| UID→包名 | `getPackageManager().getPackagesForUid(uid)`，见 `shouldTrackApp` 第 2383 行 |
| 缓存 | `uidToApp`、`uidToPackage`（`ConcurrentHashMap`） |

降级行为（值得在能力矩阵记录）：

- `getConnectionOwnerUid` 只在 TCP(6)/UDP(17) 生效，其余协议返回 `Process.INVALID_UID`。
- 工作资料/跨用户场景可能抛 `SecurityException`，此时默认按“跟踪”处理（第 2389 行）。
- system UID（<2000）默认放行。

> 建议 A1-8 的“UID 归属成功率”按协议分别统计（TCP/UDP/ICMP），因为 ICMP 天然无法归属。

---

## 7. 阻断入口

```text
isAddressAllowed(Packet)                                [第 2304 行]
   → blockKnownTracker(daddr, uid)                      [第 2432 行]
        → TrackerList.findTracker(qname/aname)           [Tracker 命中, 支持 DNS uncloaking]
        → BlockingMode / BlockingModeLogic.shouldBlockKnownTracker(...)
        → TrackerBlocklist.blockedTracker(uid, tracker)  [细粒度规则, 非 minimal 模式]
   → InternetBlocklist.blockedInternet(uid)             [整应用断网]
   → packet.allowed = false，保留 logPacket 记录
```

关键点：

- 阻断基于“域名证据”，不做 IP-only 阻断（第 2563 行注释），避免共享 IP 误伤——与项目“证据等级”理念一致。
- `BlockingMode` 有多档（含 minimal / ambiguous tracker 策略），具体见 `net.kollnig.missioncontrol.data.BlockingMode`。
- `isDomainBlocked(String)`（第 2257 行）在 NetGuard 中是 `return false` 的桩，TrackerControl 走 `blockKnownTracker` 路径，不要误接。

---

## 8. 与我们契约（`docs/09`）的映射与差异

### 8.1 字段映射（NetworkEvent）

| 我们的字段 | 底座来源 | 备注 |
|---|---|---|
| `protocol` | `Packet.protocol` 6/17/1/58 | 映射为 `TCP`/`UDP`/`ICMP` |
| `remoteIp` | `Packet.daddr` | 直接使用 |
| `remotePort` | `Packet.dport` | 直接使用 |
| `domainHint` | `dnsResolved` → `ipToHost` / `getQAName` | 可能为空 |
| `uid` | `Packet.uid` | 失败为 -1 |
| `packageName` | `uidToPackage` / `getPackagesForUid` | 失败为 `unknown` |
| `blocked` | `Packet.allowed == false` | 由 `isAddressAllowed` 决定 |
| `timestamp` | `Packet.time` | |
| `bytesIn` / `bytesOut` | `Usage` / DB 统计 | **`Packet` 不含，需额外取源** |

### 8.2 需要向 B 确认/同步的契约差异（集成点）

1. **字节数字段**：`docs/09` 的 network 事件含 `bytesIn/bytesOut`，但 `Packet` 不提供。**已决策**：P0 置空/0，`docs/09` 已加标注，P1 再补。
2. **`category` / `evidenceLevel`**：底座有 tracker 分类，但项目的 `evidenceLevel`（E1~E5）是原创概念，适配层固定映射 E2（网络元数据）即可，不把底座分类当结论。
3. **包名 vs `network` vs `vpn` 目录命名**：已决策统一使用 `network/`（`docs/19` 已同步；事件类型本身即 `network`）。

---

## 9. 适配层落点（A1-5/A1-6 实施建议）

建议在导入底座后新增适配层，不改 native、尽量少改 `ServiceSinkhole`：

```text
app/src/main/java/<our.pkg>/
├─ network/             NetworkEventCollector / ServiceSinkhole 回调适配
├─ event/               NetworkEvent → ContractEvent（docs/09）转换
├─ profile/             PackageManager 采集（A1-3）
└─ usage/               UsageStats 采集（A1-4）
```

接入点二选一：

- **方案 1（侵入小）**：在 `ServiceSinkhole` 的 4 个回调末尾调用我们的 `NetworkEventCollector.onPacket/onDns/onUid/onBlocked`。
- **方案 2（侵入更小）**：实现一个 `BroadcastReceiver`/本地 Binder，让适配层订阅，但底座目前没有广播事件出口，需要额外补一个广播发送——反而改动更大。

**当前建议方案 1**：改动集中、可回滚，且符合“团队修改另起 commit”的许可证要求。

---

## 10. 待设备验证清单（转 `spike-results.md`）

- [ ] VPN 启动后演示机可联网（A1-1）
- [ ] 能否稳定拿到 `logPacket` 连接事件
- [ ] `domainHint` 命中率（DNS 是否走底座解析链）
- [ ] TCP/UDP/ICMP 的 UID 归属成功率（A1-8）
- [ ] 至少一个测试域名可阻断且保留尝试记录（A1-6）
- [ ] IPv6 与网络切换（Wi-Fi↔蜂窝）下 VPN 是否重建
