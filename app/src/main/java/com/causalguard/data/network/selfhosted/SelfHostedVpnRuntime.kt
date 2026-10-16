package com.causalguard.data.network.selfhosted

import com.causalguard.net.core.block.DomainBlocklist

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

    /** 自研 VPN 是否正在运行（已建立 TUN 且采集中）。 */
    @Volatile
    var running: Boolean = false
        internal set
}
