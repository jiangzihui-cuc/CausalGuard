# v2.0 阶段 1 真机验证记录（自研网络底座）

> 日期：2026-10-17
> 分支：`feature/v2-t1-selfhosted-net-core`
> 设备：OPPO PJW110 / Android 16（SDK 36）/ 1080×2412
> 构建：`:app:assembleDebug`（debug 变体，含 debug-only 验收入口）
> 对应任务：[17 任务看板](17-task-board.md) V1-8；路线图见 [22](22-next-phase-readme.md)

---

## 1. 结论

自研网络底座在真机上完成 **DNS 观测面**端到端验证：

- ✅ TUN 建立成功，自研 `VpnService` 运行；
- ✅ DNS 查询经自研代理转发，设备正常解析域名（VPN 不破坏联网）；
- ✅ 观测到的连接元数据生成 `PrivacyEvent` 并写入 Room；
- ✅ UID 归属生效（`/proc` + `ConnectivityManager` 回退）；
- ✅ 基于域名的阻断生效（命中名单返回 NXDOMAIN，事件 `blocked=true`）；
- ✅ TUN 自身的 IPv6 链路本地/组播噪声已被过滤；
- ✅ 修复了自研 VPN 建立时触发连通性 watcher 重启、导致事件桥 `available=false` 的缺陷。

**未覆盖**：通用 TCP 连接元数据观测（需要用户态 TCP 栈，见 V1-7）。因此 `domainHint`
只在 TCP/UDP 流量真正经过 TUN 后才有意义；本轮 DNS 面已验证，TCP 面待后续里程碑。

---

## 2. 验证项与证据

### 2.1 TUN 建立

```text
$ adb shell ip addr show tun0
70: tun0: <POINTOPOINT,UP,LOWER_UP> mtu 1500 ...
    inet 10.111.222.1/24 scope global tun0

logcat: CausalGuardVpn: causalguard self-hosted vpn started
dumpsys: ServiceRecord com.causalguard/.data.network.selfhosted.SelfHostedVpnService
```

### 2.2 DNS 转发（VPN 开着仍可解析）

```text
$ adb shell ping -c 1 www.wikipedia.org
PING dyna.wikimedia.org (103.102.166.224) ... 1 received
```

### 2.3 事件入库

拉取 Room 数据库（`run-as`，debug 变体）后统计：

```text
privacy_event total: 9
network_event total: 9
source              : vpn
protocol            : UDP
remote              : 10.111.222.2:53
```

### 2.4 UID 归属

```text
n-1792225067369-10331-10.111.222.2:53  com.tencent.mm  uid=10331
```

说明：DNS 查询来自系统解析链路，部分可归属到具体应用；无法归属的保持 `uid=-1`、`unknown`。

### 2.5 域名阻断

```text
# 对照组
adb shell ping -c 1 www.wikipedia.org     -> 103.102.166.224（正常解析）

# 注入阻断（仅 debug 构建）
adb shell am broadcast -a com.causalguard.debug.BLOCK_DOMAIN \
  -n com.causalguard/.debug.DebugDomainBlockReceiver --es domain en.wikipedia.org
CausalGuardDebug: block domain=en.wikipedia.org added=true running=true size=1

# 被阻断域名
adb shell ping -c 1 en.wikipedia.org      -> ping: unknown host en.wikipedia.org

# 事件库
blocked events: 1
('n-1792225081235--1-10.111.222.2:53', 'unknown', -1, 'UDP', '10.111.222.2', 53, None, 1, 'vpn')
```

### 2.6 噪声过滤

首轮验证曾入库 `ff02::2` / `ff02::16`（TUN 自身 IPv6 组播）。加入
`shouldObserve()` 过滤（IPv6 链路本地/组播、IPv4 组播/广播）后，事件库不再出现该类噪声。

### 2.7 连通性 watcher 缺陷与修复

首轮验证发现：自研 VPN 建立后，`registerDefaultNetworkCallback` 把 VPN 网络当作默认网络变化，
触发 `NetworkMonitorController.restart()`，`collector.stop()` 关闭事件桥，而 `startService`
命中已运行实例时未重新开启，导致 `adapterAvailable=false`、后续事件不再入库。

修复：

1. `AndroidConnectivityWatcher` 忽略 VPN 网络的 `onAvailable`/`onLost`，只对物理网络变化重启；
2. `SelfHostedVpnService.onStartCommand` 在已运行时幂等地重新 `adapter.start()`。

修复后日志：

```text
CausalGuardNet: ignore vpn network available (self-hosted vpn)
CausalGuardDebug: status running=true adapterAvailable=true dropped=0 blockedDomains=[en.wikipedia.org]
CausalGuardNet: ingested ... blocked=true
```

---

## 3. 复现步骤

```bash
# 1. 构建并安装 debug 变体
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. 打开应用 → 设置与隐私控制可跳过；
#    首页为空时点“查看事件时间线”→ “Spike / Debug” → “Start Network Monitor (A4-5)”
#    首次会弹出系统 VPN 授权对话框，点“允许”。

# 3. 确认 TUN
adb shell ip addr show tun0

# 4. DNS 转发
adb shell ping -c 1 www.wikipedia.org

# 5. 注入阻断（仅 debug 构建）
adb shell am broadcast -a com.causalguard.debug.BLOCK_DOMAIN \
  -n com.causalguard/.debug.DebugDomainBlockReceiver --es domain en.wikipedia.org
adb shell ping -c 1 en.wikipedia.org          # 预期：unknown host

# 6. 查询自研底座状态
adb shell am broadcast -a com.causalguard.debug.STATUS \
  -n com.causalguard/.debug.DebugStatusReceiver
adb logcat -s CausalGuardDebug CausalGuardVpn CausalGuardNet

# 7. 拉取事件库（debug 变体）
adb exec-out run-as com.causalguard cat databases/causalguard.db > cg.db
adb exec-out run-as com.causalguard cat databases/causalguard.db-wal > cg.db-wal
```

> debug 验收入口（`app/src/debug/`）不会进入 release APK；release 构建无该广播接收器。

---

## 4. 已知限制

1. **TCP 数据面未实现**：TUN 只路由 DNS 子网，TCP 流量直连、不经 VPN，因此不会被观测；
   要观测任意 TCP 连接元数据，需要路由全网卡并实现用户态 TCP 栈（V1-7）。
2. **DNS 归属有限**：部分 DNS 查询可归属到应用，部分只能 `unknown`；这是 Android 平台边界，
   保持诚实降级，不猜测。
3. **首页加载**：真机上首页在已有事件时可能长期停在“正在加载当前数据集...”（既有问题，
   与本阶段底座改造无关），验收通过调试面板与事件库完成。

---

## 5. V1-7a：UDP 用户态转发（2026-10-18 真机验证通过）

实现：

- `net-core` 新增 `relay/UdpRelay`：按五元组复用 `protected DatagramSocket`，把 TUN 收到的
  UDP 报文转发到远端，并把响应重新封装为 IPv4/UDP 包写回 TUN；带空闲流回收与流数上限；
  V1-7a 只支持 IPv4（IPv6 留待 V1-7c）。
- `SelfHostedVpnService`：非 DNS 的 UDP 报文交给 `UdpRelay`；新增
  `SelfHostedVpnRuntime.dataPlaneRoutes`（默认空）用于分阶段注入捕获路由；
  `ACTION_REBUILD` 支持重建 TUN。
- debug-only：`DebugRouteReceiver`（注入捕获路由）、`DebugUdpProbeReceiver`（发 UDP 探针）。

单测：`UdpRelayTest` 3 项（响应回写报文、拒绝非 UDP/IPv6、流数上限），`net-core` 共 29 项全绿。

真机复现步骤：

```bash
# 启动监测（调试面板 Start Network Monitor）后，注入一条测试捕获路由
adb shell am broadcast -a com.causalguard.debug.ADD_ROUTE \
  -n com.causalguard/.debug.DebugRouteReceiver --es route 114.114.114.114/32

# 发 UDP 探针（DNS 查询），若数据面可用会收到响应
adb shell am broadcast -a com.causalguard.debug.UDP_PROBE \
  -n com.causalguard/.debug.DebugUdpProbeReceiver \
  --es host 114.114.114.114 --ei port 53

adb logcat -s CausalGuardDebug CausalGuardVpn CausalGuardNet
```

实测证据（2026-10-18，PJW110 / Android 16）：

```text
CausalGuardDebug: add route=114.114.114.114/32 added=true routes=[114.114.114.114/32]
CausalGuardVpn  : causalguard self-hosted vpn started (routes=[114.114.114.114/32])
ip route        : 114.114.114.114 dev tun0 table 1073 proto static scope link
CausalGuardDebug: udp probe ok host=114.114.114.114 port=53 responseLength=61
CausalGuardNet  : ingested id=...-10450-114.114.114.114:53 app=com.causalguard blocked=false
CausalGuardDebug: status running=true adapterAvailable=true dropped=0
DB              : ('...-114.114.114.114:53','com.causalguard',10450,'UDP','114.114.114.114',53,0,'vpn')
```

结论：TUN 捕获 → `UdpRelay` protected 转发 → 响应回写 TUN → 应用收到响应（61 字节），
且事件以 UID 10450 / `com.causalguard` 入库；DNS 对照组仍正常。**V1-7a 真机通过。**

> 注意：测试路由只用于验证数据面，验证完应重启应用（`dataPlaneRoutes` 为进程内状态）恢复默认 DNS-only。
> **在 V1-7b TCP 数据面就绪前，禁止注入 `0.0.0.0/0`。**

---

## 6. V1-7b：TCP 用户态中继（2026-10-18 真机通过，IPv4 MVP）

实现：

- `net-core` 扩展 `IpPacket` / `IpPacketParser`：解析 TCP seq/ack/window 与 options（MSS、window scale）。
- 新增 `TcpPacketBuilder`：构造 IPv4/TCP 报文（含 IP/TCP 校验和、MSS/window scale 选项）。
- 新增 `relay/TcpRelay`：SYN → protected Socket 连接远端 → SYN-ACK；双向数据；
  ACK 释放发送缓冲；固定 RTO 的 Go-Back-N 重传；FIN/RST；空闲流回收与流数上限。
- `SelfHostedVpnService` 接入 `TcpRelay`；TCP 只在 SYN 时产出一条事件，数据/ACK 不入库。

真机问题与修复：

- `VpnService.protect(java.net.Socket)` 在 socket 尚未 bind/connect、内核 fd 未创建时返回
  `false`（UDP 的 `DatagramSocket` 构造时即绑定，所以 V1-7a 正常）。修复：先
  `socket.bind(InetSocketAddress(0))` 创建 fd，再 `protect`，最后 `connect`。

实测证据（PJW110 / Android 16）：

```text
CausalGuardVpn  : tcp syn -> 220.181.111.232:80
CausalGuardVpn  : tcp connected 220.181.111.232:80
CausalGuardDebug: tcp probe connected host=220.181.111.232 port=80
CausalGuardDebug: tcp probe request sent host=220.181.111.232 port=80
CausalGuardDebug: tcp probe ok host=220.181.111.232 port=80 firstLine=HTTP/1.1 200 OK
DB              : ('...-10450-220.181.111.232:80','com.causalguard',10450,'TCP','220.181.111.232',80,0,'vpn')
```

DNS 对照组仍正常（`www.qq.com` 解析并 ping 通）。

复现步骤：

```bash
adb shell am broadcast -a com.causalguard.debug.ADD_ROUTE \
  -n com.causalguard/.debug.DebugRouteReceiver --es route 220.181.111.232/32
adb shell am broadcast -a com.causalguard.debug.TCP_PROBE \
  -n com.causalguard/.debug.DebugTcpProbeReceiver --es host 220.181.111.232 --ei port 80
adb logcat -s CausalGuardDebug CausalGuardVpn CausalGuardNet
```

范围与限制（V1-7c / 7d）：

- 仅 IPv4；IPv6、分片重组、乱序缓存、SACK/窗口缩放、序列号回绕未实现。
- 发送端为 MVP（固定 RTO Go-Back-N），弱网吞吐/重传效率需 7c 验证。
- 默认路由仍只含 DNS 子网；`0.0.0.0/0` 需在 7c/7d 完成并真机验证后再切换。
