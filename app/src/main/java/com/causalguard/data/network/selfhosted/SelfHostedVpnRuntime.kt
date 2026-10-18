package com.causalguard.data.network.selfhosted

import com.causalguard.net.core.block.DomainBlocklist
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 自研网络底座的进程内单例运行时（v2.0 阶段 1）。
 *
 * 自研 `VpnService` 与业务侧（DI 注入的 [SelfHostedNetworkEventSource]、
 * [SelfHostedDomainBlockController]）在**同一进程**，因此共享同一份
 * [adapter] 与 [blocklist]：服务写事件、业务读事件；阻断控制器写名单、服务读名单。
 */
object SelfHostedVpnRuntime {

    /** 事件桥：由 [SelfHostedVpnService] 写入，由采集链路读取。 */
    val adapter: SelfHostedNetworkAdapter = SelfHostedNetworkAdapter()

    /** 域名阻断名单：由阻断控制器写入，DNS 代理读取。 */
    val blocklist: DomainBlocklist = DomainBlocklist()

    /**
     * V1-7a：额外捕获路由（CIDR，如 `114.114.114.114/32`）。
     *
     * 默认空 → TUN 只路由 DNS 子网，行为与阶段 1 一致。
     * **TCP 数据面（V1-7b）就绪前，禁止加入 `0.0.0.0/0`**，否则 TCP 会断网。
     * 仅供调试/分阶段验证，由 debug 接收器注入；release 中保持空集。
     */
    private val routes = CopyOnWriteArraySet<String>()

    val dataPlaneRoutes: Set<String>
        get() = routes.toSet()

    /** 加入一条捕获路由；格式非法返回 false。 */
    fun addDataPlaneRoute(cidr: String): Boolean {
        val normalized = cidr.trim()
        if (!ROUTE_PATTERN.matches(normalized)) return false
        return routes.add(normalized)
    }

    fun clearDataPlaneRoutes() {
        routes.clear()
    }

    /** 自研 VPN 是否正在运行（已建立 TUN 且采集中）。 */
    @Volatile
    var running: Boolean = false
        internal set

    /**
     * V1-7d：是否启用“全隧道”（额外路由 `0.0.0.0/0`）。
     *
     * **默认关闭**。仅在调试/分阶段验证时开启；开启后 IPv4 全部流量进入 TUN，
     * 由 `UdpRelay`/`TcpRelay` 转发。ICMP 不转发（ping 会失败），IPv6 默认不接管。
     */
    @Volatile
    var fullTunnelEnabled: Boolean = false
        private set

    /**
     * V1-7d：全隧道是否只允许本应用走 VPN（`addAllowedApplication(com.causalguard)`）。
     *
     * 真机验证默认 `true`：只让本应用的调试探针进入 TUN，**不影响其它 App 与 adb 连接**，
     * 避免数据面缺陷导致整机断网。生产全隧道验证通过后再置 `false`。
     */
    @Volatile
    var fullTunnelOnlyOwnApp: Boolean = true
        private set

    fun setFullTunnel(enabled: Boolean, onlyOwnApp: Boolean = true) {
        fullTunnelEnabled = enabled
        fullTunnelOnlyOwnApp = onlyOwnApp
    }

    private val ROUTE_PATTERN = Regex("""[0-9a-fA-F:.]+/\d{1,3}""")
}
